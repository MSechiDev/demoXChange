package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ItemSummaryDto;
import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OfferService {
    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final ItemRepository itemRepository;
    private final ExchangeRepository exchangeRepository;
    private final AppUserRepository appUserRepository;

    public OfferService(OfferRepository offerRepository, ListingRepository listingRepository, ItemRepository itemRepository, ExchangeRepository exchangeRepository, AppUserRepository appUserRepository) {
        this.offerRepository = offerRepository;
        this.listingRepository = listingRepository;
        this.itemRepository = itemRepository;
        this.exchangeRepository = exchangeRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public OfferDto makeOffer(Long listingId, List<Long> itemIds, String message, Long userId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("listing_not_found", "Listing not found: " + listingId));

        if (listing.getStatus() != ListingStatus.attivo && listing.getStatus() != ListingStatus.in_trattativa) {
            throw new BadRequestException("listing_not_available",
                    "You can only make an offer on listings that are 'attivo' or 'in_trattativa'.");
        }

        AppUser offerer = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + userId));

        List<Item> items = itemRepository.findAllById(itemIds);

        boolean tuttiValidi = items.stream()
                .allMatch(item -> item.getOwner().getId().equals(userId));

        if (itemIds.size() != items.size() || !tuttiValidi) {
            throw new BadRequestException("items_not_found", "One or more items have not been found");
        }

        Offer offer = new Offer(listing, offerer, offerer);
        offer.setMessage(message);
        offer.getItems().addAll(items);

        Offer saved = offerRepository.save(offer);

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<OfferDto> sentOffers(Long userId) {
        List<Offer> offers = offerRepository.findByOffererId(userId);

        return offers.stream()
                     .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<OfferDto> receivedOffers(Long userId) {
        List<Offer> offers = offerRepository.findByListing_Item_Owner_Id(userId);

        return offers.stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public OfferDto approveOffer(Long offerId, Long userId) {
        Offer offer = offerRepository.findById(offerId)
                                     .orElseThrow(() -> new NotFoundException("offer_not_found", "Offer not found"));

        if (!offer.getListing().getItem().getOwner().getId().equals(userId)) {
            throw new ForbiddenException("offer_not_owned", "You can only approve offers that are yours.");
        }

        if (offer.getStatus() != OfferStatus.in_attesa) {
            throw new BadRequestException("not_valid_status", "You can only approve offers that are in attesa.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        offer.setStatus(OfferStatus.accettata);
        offer.setRespondedAt(now);

        List<Offer> otherPendingOffers = offerRepository.findByListingIdAndStatus(
                    offer.getListing().getId(), OfferStatus.in_attesa);

        otherPendingOffers.stream()
                          .filter(o -> !o.getId().equals(offer.getId()))
                          .forEach(o -> {
                              o.setStatus(OfferStatus.rifiutata);
                              o.setRespondedAt(now);
                          });

        Exchange exchange = new Exchange(offer);
        exchangeRepository.save(exchange);

        return toDto(offer);
    }

    @Transactional
    public OfferDto counterOffer(Long offerId, List<Long> itemIds, String message, Long userId) {
        Offer parentOffer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("offer_not_found", "Offer not found"));

        if (parentOffer.getStatus() != OfferStatus.in_attesa) {
            throw new BadRequestException("not_valid_status",
                    "You can only counter offers that are in_attesa.");
        }

        Listing listing = parentOffer.getListing();

        if (!listing.getItem().getOwner().getId().equals(userId)) {
            throw new ForbiddenException("offer_not_owned",
                    "You can only counter offers on your own listings.");
        }

        AppUser offerer = parentOffer.getOfferer();
        AppUser createdBy = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + userId));

        List<Item> items = itemRepository.findAllById(itemIds);

        boolean tuttiValidi = items.stream()
                .allMatch(item -> item.getOwner().getId().equals(userId));

        if (itemIds.size() != items.size() || !tuttiValidi) {
            throw new BadRequestException("items_not_found", "One or more items have not been found");
        }

        parentOffer.setStatus(OfferStatus.controproposta);
        parentOffer.setRespondedAt(OffsetDateTime.now());

        Offer counter = new Offer(listing, offerer, createdBy);
        counter.setMessage(message);
        counter.setParentOffer(parentOffer);
        counter.getItems().addAll(items);

        Offer saved = offerRepository.save(counter);

        return toDto(saved);
    }

    private OfferDto toDto(Offer offer) {
        List<ItemSummaryDto> offeredItemsDto = offer.getItems().stream()
                .map(item -> new ItemSummaryDto(
                        item.getId(),
                        item.getTitle(),
                        item.getItemCondition(),
                        item.getEstimatedValue(),
                        item.getImages().isEmpty() ? null : item.getImages().get(0).getUrl()
                ))
                .toList();

        return new OfferDto(
                offer.getId(),
                offer.getOfferer().getId(),
                offer.getOfferer().getUsername(),
                offeredItemsDto,
                offer.getMessage(),
                offer.getStatus()
            );
        }

}

