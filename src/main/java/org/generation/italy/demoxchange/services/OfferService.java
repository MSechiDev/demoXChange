package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ItemImage;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.repositories.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OfferService {
    private final OfferRepository offerRepository;
    private final ListingRepository listingRepository;
    private final ItemRepository itemRepository;
    private final ExchangeRepository exchangeRepository;

    public OfferService(OfferRepository offerRepository, ListingRepository listingRepository, ItemRepository itemRepository, ExchangeRepository exchangeRepository) {
        this.offerRepository = offerRepository;
        this.listingRepository = listingRepository;
        this.itemRepository = itemRepository;
        this.exchangeRepository = exchangeRepository;
    }

    @Transactional
    public OfferDto makeOffer(Long listingId, List<Long> itemIds, String message, Long userId) {

    }

    @Transactional(readOnly = true)
    public List<OfferDto> sentOffers(Long userId) {

    }

    @Transactional(readOnly = true)
    public List<OfferDto> receivedOffers(Long userId) {

    }

    @Transactional
    public OfferDto approveOffer(Long offerId, Long userId) {

    }

    @Transactional
    public OfferDto counterOffer(Long offerId, List<Long> itemIds, String message, Long userId) {

    }
}

