package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateListingRequest;
import org.generation.italy.demoxchange.model.dto.ListingDto;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.entities.ListingStatus;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ListingService {
    private final AppUserRepository appUserRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final ListingRepository listingRepository;

    public ListingService(AppUserRepository appUserRepository, CategoryRepository categoryRepository, ItemRepository itemRepository, ListingRepository listingRepository) {
        this.appUserRepository = appUserRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.listingRepository = listingRepository;
    }

    @Transactional
    public ListingDto createListing(Long ownerId, CreateListingRequest request) {
        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new NotFoundException("ITEM_NOT_FOUND", "Item not found"));
        if (!item.getOwner().getId().equals(owner.getId())) {
            throw new NotFoundException("ITEM_NOT_FOUND", "Item not found");
        }
        if (listingRepository.existsByItemId(item.getId())) {
            throw new ConflictException("LISTING_ALREADY_EXISTS", "This item is already listed");
        }

        if (request.acceptedCategoryIds() == null || request.acceptedCategoryIds().isEmpty()) {
            throw new BadRequestException("INVALID_LISTING", "At least one accepted category is required");
        }

        Set<Long> categoryIds = new LinkedHashSet<>(request.acceptedCategoryIds());
        List<Category> acceptedCategories = categoryRepository.findAllById(categoryIds);
        if (acceptedCategories.size() != categoryIds.size()) {
            Set<Long> foundIds = acceptedCategories.stream().map(Category::getId).collect(Collectors.toSet());
            Long missingId = categoryIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElse(null);
            throw new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + missingId);
        }

        Listing listing = new Listing(item, request.city());
        listing.setStatus(ListingStatus.attivo);
        listing.setAcceptedCategories(new HashSet<>(acceptedCategories));

        Listing saved = listingRepository.save(listing);
        return toDto(saved);
    }

    @Transactional
    public ListingDto updateStatus(Long ownerId, Long listingId, ListingStatus status) {
        if (status == null) {
            throw new BadRequestException("INVALID_STATUS", "Status is required");
        }

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("LISTING_NOT_FOUND", "Listing not found"));

        if (!listing.getItem().getOwner().getId().equals(ownerId)) {
            throw new NotFoundException("LISTING_NOT_FOUND", "Listing not found");
        }

        listing.setStatus(status);
        return toDto(listingRepository.saveAndFlush(listing));
    }

    @Transactional(readOnly = true)
    public List<ListingDto> findByOwner(Long ownerId) {
        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        return listingRepository.findByItemOwner(owner).stream()
                .map(ListingService::toDto)
                .toList();
    }

    private static ListingDto toDto(Listing listing) {
        return new ListingDto(
                listing.getId(),
                listing.getItem().getId(),
                listing.getItem().getOwner().getId(),
                listing.getCity(),
                listing.getStatus(),
                listing.getPublishedAt(),
                listing.getUpdatedAt(),
                listing.getAcceptedCategories().stream()
                        .map(Category::getId)
                        .sorted()
                        .toList()
        );
    }
}
