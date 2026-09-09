package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ItemSummaryDto;
import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

        if (listing.getStatus() != ListingStatus.attivo) {
            throw new BadRequestException("listing_not_available",
                    "You can only make an offer on listings that are 'attivo'.");
        }

        if (listing.getItem().getOwner().getId().equals(userId)) {
            throw new BadRequestException("self_offer_not_allowed", "You cannot make an offer on your own listing.");
        }

        boolean hasPendingOffer = offerRepository.findByListingIdAndStatus(listingId, OfferStatus.in_attesa)
                .stream()
                .anyMatch(o -> o.getOfferer().getId().equals(userId));

        if (hasPendingOffer) {
            throw new ConflictException("offer_already_pending",
                    "You already have a pending offer on this listing.");
        }

        AppUser offerer = appUserRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + userId));

        List<Item> items = itemRepository.findAllById(itemIds);

        boolean tuttiValidi = items.stream()
                .allMatch(item -> item.getOwner().getId().equals(userId));

        if (itemIds.size() != items.size() || !tuttiValidi) {
            throw new BadRequestException("items_not_found", "One or more items have not been found");
        }

        assertItemsMatchAcceptedCategories(listing, items);

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

        if (!isResponder(offer, userId)) {
            throw new ForbiddenException("offer_not_owned", "You can only approve offers addressed to you.");
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

        offer.getListing().setStatus(ListingStatus.in_trattativa);

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

        assertItemsMatchAcceptedCategories(listing, items);

        parentOffer.setStatus(OfferStatus.controproposta);
        parentOffer.setRespondedAt(OffsetDateTime.now());
        // Flush the parent's status change before inserting the counter: the counter row reuses
        // the same (listing_id, offerer_id) and defaults to in_attesa, so without this the INSERT
        // (forced immediately by the IDENTITY generator) can race the still-pending UPDATE and trip
        // the DB's "one pending offer per listing/offerer" constraint.
        offerRepository.saveAndFlush(parentOffer);

        Offer counter = new Offer(listing, offerer, createdBy);
        counter.setMessage(message);
        counter.setParentOffer(parentOffer);
        counter.getItems().addAll(items);

        Offer saved = offerRepository.save(counter);

        return toDto(saved);
    }

    // Offers and counter-offers alternate turns: whoever did NOT create this pending offer must respond to it.
    private static boolean isResponder(Offer offer, Long userId) {
        Long offererId = offer.getOfferer().getId();
        Long createdById = offer.getCreatedBy().getId();
        if (createdById.equals(offererId)) {
            return offer.getListing().getItem().getOwner().getId().equals(userId);
        }
        return offererId.equals(userId);
    }

    private static void assertItemsMatchAcceptedCategories(Listing listing, List<Item> items) {
        Set<Long> acceptedCategoryIds = listing.getAcceptedCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        boolean allAccepted = items.stream()
                .allMatch(item -> acceptedCategoryIds.contains(item.getCategory().getId()));

        if (!allAccepted) {
            throw new BadRequestException("item_category_not_accepted",
                    "One or more offered items are not in a category accepted by this listing.");
        }
    }

    @Transactional
    public OfferDto rejectOffer(Long offerId, Long userId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("offer_not_found", "Offer not found"));

        if (!isResponder(offer, userId)) {
            throw new ForbiddenException("offer_not_owned", "You can only reject offers addressed to you.");
        }

        if (offer.getStatus() != OfferStatus.in_attesa) {
            throw new BadRequestException("not_valid_status", "You can only reject offers that are in_attesa.");
        }

        offer.setStatus(OfferStatus.rifiutata);
        offer.setRespondedAt(OffsetDateTime.now());

        return toDto(offer);
    }

    @Transactional
    public OfferDto cancelOffer(Long offerId, Long userId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new NotFoundException("offer_not_found", "Offer not found"));

        if (!offer.getCreatedBy().getId().equals(userId)) {
            throw new ForbiddenException("offer_not_owned", "You can only cancel offers you created.");
        }

        if (offer.getStatus() != OfferStatus.in_attesa) {
            throw new BadRequestException("not_valid_status", "You can only cancel offers that are in_attesa.");
        }

        offer.setStatus(OfferStatus.annullata);
        offer.setRespondedAt(OffsetDateTime.now());

        return toDto(offer);
    }

    private OfferDto toDto(Offer offer) {
        List<ItemSummaryDto> offeredItemsDto = offer.getItems().stream()
                .map(item -> new ItemSummaryDto(
                        item.getId(),
                        item.getTitle(),
                        item.getItemCondition(),
                        item.getEstimatedValue(),
                        item.getImages().isEmpty() ? null : item.getImages().getFirst().getUrl()
                ))
                .toList();

        return new OfferDto(
                offer.getId(),
                offer.getOfferer().getId(),
                offer.getOfferer().getUsername(),
                offeredItemsDto,
                offer.getMessage(),
                offer.getStatus(),
                offer.getCreatedBy().getId(),
                offer.getParentOffer() != null ? offer.getParentOffer().getId() : null
            );
        }

}

