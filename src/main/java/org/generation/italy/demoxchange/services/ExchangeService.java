package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ExchangeDto;
import org.generation.italy.demoxchange.model.dto.UpdateExchangeLogisticsRequest;
import org.generation.italy.demoxchange.model.entities.Exchange;
import org.generation.italy.demoxchange.model.entities.ExchangeStatus;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ListingStatus;
import org.generation.italy.demoxchange.model.entities.Offer;
import org.generation.italy.demoxchange.model.entities.OfferStatus;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.ExchangeRepository;
import org.generation.italy.demoxchange.model.repositories.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExchangeService {
    private final ExchangeRepository exchangeRepository;
    private final ReviewRepository reviewRepository;

    public ExchangeService(ExchangeRepository exchangeRepository, ReviewRepository reviewRepository) {
        this.exchangeRepository = exchangeRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public List<ExchangeDto> findMine(long userId) {
        return exchangeRepository.findAllForUser(userId).stream()
                .map(exchange -> toDto(exchange, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExchangeDto findById(long id, long viewerId) {
        return toDto(getOrThrow(id), viewerId);
    }

    @Transactional
    public ExchangeDto confirm(long id, long userId) {
        Exchange exchange = getOrThrow(id);

        if (exchange.getStatus() != ExchangeStatus.in_corso) {
            throw new BadRequestException("not_valid_status", "You can only confirm exchanges that are in_corso.");
        }

        long ownerId = exchange.getOffer().getListing().getItem().getOwner().getId();
        long offererId = exchange.getOffer().getOfferer().getId();

        boolean isOwner = userId == ownerId;
        boolean isOfferer = userId == offererId;

        if (!isOwner && !isOfferer) {
            throw new ForbiddenException("not_participant", "You are not part of this exchange.");
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (isOwner) {
            if (exchange.getOwnerConfirmedAt() != null) {
                throw new ConflictException("already_confirmed", "You have already confirmed this exchange.");
            }
            exchange.setOwnerConfirmedAt(now);
        } else {
            if (exchange.getOffererConfirmedAt() != null) {
                throw new ConflictException("already_confirmed", "You have already confirmed this exchange.");
            }
            exchange.setOffererConfirmedAt(now);
        }

        if (exchange.getOwnerConfirmedAt() != null && exchange.getOffererConfirmedAt() != null) {
            exchange.setStatus(ExchangeStatus.completato);
            exchange.setCompletedAt(now);
            exchange.getOffer().getListing().setStatus(ListingStatus.scambiato);
            // Both sides of the trade are now handed over: archive them so they drop out of
            // search/offers instead of looking available while actually already exchanged.
            // A counter-offer only carries the counterer's own items, not the original bidder's,
            // so walk the whole parent chain to catch every item that changed hands.
            exchange.getOffer().getListing().getItem().setArchived(true);
            collectAllOfferedItems(exchange.getOffer()).forEach(item -> item.setArchived(true));
        }

        return toDto(exchange, userId);
    }

    @Transactional
    public ExchangeDto cancel(long id, long userId) {
        Exchange exchange = getOrThrow(id);

        if (exchange.getStatus() != ExchangeStatus.in_corso) {
            throw new BadRequestException("not_valid_status", "You can only cancel exchanges that are in_corso.");
        }

        long ownerId = exchange.getOffer().getListing().getItem().getOwner().getId();
        long offererId = exchange.getOffer().getOfferer().getId();

        if (userId != ownerId && userId != offererId) {
            throw new ForbiddenException("not_participant", "You are not part of this exchange.");
        }

        exchange.setStatus(ExchangeStatus.annullato);
        exchange.getOffer().getListing().setStatus(ListingStatus.attivo);
        exchange.getOffer().setStatus(OfferStatus.annullata);
        exchange.getOffer().setRespondedAt(OffsetDateTime.now());

        return toDto(exchange, userId);
    }

    @Transactional
    public ExchangeDto updateLogistics(long id, long userId, UpdateExchangeLogisticsRequest request) {
        if (request.location() == null && request.method() == null) {
            throw new BadRequestException("invalid_request", "At least one of location or method is required.");
        }

        Exchange exchange = getOrThrow(id);

        if (exchange.getStatus() != ExchangeStatus.in_corso) {
            throw new BadRequestException("not_valid_status", "You can only set logistics on exchanges that are in_corso.");
        }

        long ownerId = exchange.getOffer().getListing().getItem().getOwner().getId();
        long offererId = exchange.getOffer().getOfferer().getId();

        if (userId != ownerId && userId != offererId) {
            throw new ForbiddenException("not_participant", "You are not part of this exchange.");
        }

        if (request.location() != null) {
            exchange.setLocation(request.location());
        }
        if (request.method() != null) {
            exchange.setMethod(request.method());
        }

        return toDto(exchange, userId);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(long exchangeId, long userId) {
        return exchangeRepository.findById(exchangeId)
                .map(exchange -> userId == exchange.getOffer().getOfferer().getId()
                        || userId == exchange.getOffer().getListing().getItem().getOwner().getId())
                .orElse(false);
    }

    private static Set<Item> collectAllOfferedItems(Offer offer) {
        Set<Item> items = new HashSet<>();
        for (Offer current = offer; current != null; current = current.getParentOffer()) {
            items.addAll(current.getItems());
        }
        return items;
    }

    private Exchange getOrThrow(long id) {
        return exchangeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("exchange_not_found", "Exchange not found: " + id));
    }

    private ExchangeDto toDto(Exchange exchange, long viewerId) {
        return new ExchangeDto(
                exchange.getId(),
                exchange.getOffer().getId(),
                exchange.getOffer().getListing().getId(),
                exchange.getOffer().getListing().getItem().getOwner().getId(),
                exchange.getOffer().getOfferer().getId(),
                exchange.getStatus(),
                exchange.getOwnerConfirmedAt(),
                exchange.getOffererConfirmedAt(),
                exchange.getCompletedAt(),
                exchange.getCreatedAt(),
                reviewRepository.existsByExchangeIdAndAuthorId(exchange.getId(), viewerId),
                exchange.getLocation(),
                exchange.getMethod()
        );
    }
}
