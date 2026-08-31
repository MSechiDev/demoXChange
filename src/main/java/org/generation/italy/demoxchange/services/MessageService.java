package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.MessageDto;
import org.generation.italy.demoxchange.model.dto.SendMessageRequest;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Message;
import org.generation.italy.demoxchange.model.entities.Offer;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.MessageRepository;
import org.generation.italy.demoxchange.model.repositories.OfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final OfferRepository offerRepository;
    private final AppUserRepository appUserRepository;

    public MessageService(MessageRepository messageRepository, OfferRepository offerRepository, AppUserRepository appUserRepository) {
        this.messageRepository = messageRepository;
        this.offerRepository = offerRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<MessageDto> findMine(long userId, boolean unreadOnly) {
        List<Message> messages = unreadOnly
                ? messageRepository.findUnreadForUser(userId)
                : messageRepository.findLatestMessagePerThreadForUser(userId);
        return messages.stream()
                .map(MessageService::toDto)
                .toList();
    }

    @Transactional
    public List<MessageDto> findThread(long offerId, long userId) {
        Offer rootOffer = resolveRootOffer(getOfferOrThrow(offerId));
        messageRepository.markThreadAsRead(rootOffer.getId(), userId, OffsetDateTime.now());
        return messageRepository.findByOfferIdOrderBySentAtAsc(rootOffer.getId()).stream()
                .map(MessageService::toDto)
                .toList();
    }

    @Transactional
    public MessageDto send(long offerId, long senderId, SendMessageRequest request) {
        Offer rootOffer = resolveRootOffer(getOfferOrThrow(offerId));
        AppUser sender = appUserRepository.findById(senderId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + senderId));

        Message message = new Message(rootOffer, sender, request.body());
        Message saved = messageRepository.save(message);
        return toDto(saved);
    }

    @Transactional
    public void delete(long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("message_not_found", "Message not found: " + id));
        messageRepository.delete(message);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(long offerId, long userId) {
        return offerRepository.findById(offerId)
                .map(offer -> isParticipantOf(resolveRootOffer(offer), userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isSender(long messageId, long userId) {
        return messageRepository.findById(messageId)
                .map(message -> message.getSender().getId() == userId)
                .orElse(false);
    }

    private Offer resolveRootOffer(Offer offer) {
        Offer current = offer;
        while (current.getParentOffer() != null) {
            current = current.getParentOffer();
        }
        return current;
    }

    private static boolean isParticipantOf(Offer rootOffer, long userId) {
        return rootOffer.getOfferer().getId() == userId
                || rootOffer.getListing().getItem().getOwner().getId() == userId;
    }

    private Offer getOfferOrThrow(long offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("offer_not_found", "Offer not found: " + offerId));
    }

    private static MessageDto toDto(Message message) {
        return new MessageDto(
                message.getId(),
                message.getOffer().getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getBody(),
                message.getSentAt(),
                message.getReadAt()
        );
    }
}
