package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ItemImageDto;
import org.generation.italy.demoxchange.model.dto.ListingDetailDto;
import org.generation.italy.demoxchange.model.dto.ListingSearchDto;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
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

    @Transactional(readOnly = true)
    public ListingDetailDto getListingDetail(Long id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("LISTING_NOT_FOUND", "Listing not found"));
        return toDetailDto(listing);
    }

    private static ListingDetailDto toDetailDto(Listing listing) {
        Item item = listing.getItem();
        return new ListingDetailDto(
                listing.getId(),
                listing.getCity(),
                listing.getStatus() != null ? listing.getStatus().name() : null,
                listing.getPublishedAt(),
                item != null ? item.getOwner().getId() : null,
                item != null ? item.getId() : null,
                item != null ? item.getTitle() : null,
                item != null ? item.getDescription() : null,
                item != null ? item.getEstimatedValue() : null,
                item != null ? item.getItemCondition() : null,
                (item != null && item.getCategory() != null) ? item.getCategory().getId() : null,
                (item != null && item.getCategory() != null) ? item.getCategory().getName() : null,
                listing.getAcceptedCategories().stream().map(Category::getId).sorted().toList(),
                item != null
                        ? item.getImages().stream()
                                .map(img -> new ItemImageDto(img.getId(), item.getId(), img.getUrl(), img.getDisplayOrder(), img.getCreatedAt()))
                                .toList()
                        : List.of()
        );
    }

    private static ListingSearchDto toDto(Listing listing) {
        List<Category> sortedAcceptedCategories = listing.getAcceptedCategories().stream()
                .sorted(java.util.Comparator.comparing(Category::getId))
                .toList();
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
                        ? listing.getItem().getCategory().getName() : null,
                sortedAcceptedCategories.stream().map(Category::getId).toList(),
                sortedAcceptedCategories.stream().map(Category::getName).toList(),
                (listing.getItem() != null && !listing.getItem().getImages().isEmpty())
                        ? listing.getItem().getImages().get(0).getUrl() : null
        );
    }
}