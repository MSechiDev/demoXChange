package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ListingSearchDto;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ListingSearchService {

    private final ListingRepository listingRepository;

    public ListingSearchService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    @Transactional(readOnly = true)
    public List<ListingSearchDto> searchListings(String keyword, Long categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
        return listingRepository.searchListings(keyword, categoryId, minPrice, maxPrice)
                .stream()
                .map(ListingSearchService::toDto)
                .toList();
    }

    private static ListingSearchDto toDto(Listing listing) {
        return new ListingSearchDto(
                listing.getId(),
                listing.getCity(),
                listing.getStatus() != null ? listing.getStatus().name() : null,
                listing.getPublishedAt(),
                listing.getItem() != null ? listing.getItem().getId() : null,
                listing.getItem() != null ? listing.getItem().getTitle() : null,
                listing.getItem() != null ? listing.getItem().getDescription() : null,
                listing.getItem() != null ? listing.getItem().getEstimatedValue() : null,
                (listing.getItem() != null && listing.getItem().getCategory() != null)
                        ? listing.getItem().getCategory().getId() : null,
                (listing.getItem() != null && listing.getItem().getCategory() != null)
                        ? listing.getItem().getCategory().getName() : null
        );
    }
}