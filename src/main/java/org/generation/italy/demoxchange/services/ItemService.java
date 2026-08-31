package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateItemRequest;
import org.generation.italy.demoxchange.model.dto.ItemDto;
import org.generation.italy.demoxchange.model.dto.ItemImageDto;
import org.generation.italy.demoxchange.model.dto.UpdateItemRequest;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ItemImage;
import org.generation.italy.demoxchange.model.entities.ItemCondition;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ItemService {
    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;

    public ItemService(
            ItemRepository itemRepository,
            CategoryRepository categoryRepository,
            AppUserRepository appUserRepository
    ) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> search(
            long ownerId,
            Long categoryId,
            ItemCondition condition,
            BigDecimal minValue,
            BigDecimal maxValue,
            boolean includeArchived,
            String q
    ) {
        if (minValue != null && maxValue != null && minValue.compareTo(maxValue) > 0) {
            throw new BadRequestException("invalid_price_range", "minValue must be less than or equal to maxValue");
        }
        String normalized = (q == null || q.isBlank()) ? null : q.trim();
        return itemRepository.search(ownerId, categoryId, condition, includeArchived, minValue, maxValue, normalized)
                .stream()
                .map(ItemService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemDto findByIdForOwner(long id, long ownerId) {
        return toDto(getOwnedOrThrow(id, ownerId));
    }

    @Transactional
    public ItemDto create(long ownerId, CreateItemRequest request) {
        Category category = getActiveCategoryOrThrow(request.categoryId());
        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + ownerId));

        Item item = new Item(owner, category, request.title().trim(), request.description().trim(), request.itemCondition());
        item.setEstimatedValue(request.estimatedValue());

        return toDto(itemRepository.save(item));
    }

    @Transactional
    public ItemDto update(long id, long ownerId, UpdateItemRequest request) {
        Item item = getOwnedOrThrow(id, ownerId);

        if (!request.categoryId().equals(item.getCategory().getId())) {
            item.setCategory(getActiveCategoryOrThrow(request.categoryId()));
        }
        item.setTitle(request.title().trim());
        item.setDescription(request.description().trim());
        item.setEstimatedValue(request.estimatedValue());
        item.setItemCondition(request.itemCondition());
        item.setArchived(request.archived());

        return toDto(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(long id, long ownerId) {
        Item item = getOwnedOrThrow(id, ownerId);
        item.setArchived(true);
        itemRepository.save(item);
    }

    private Item getOwnedOrThrow(long id, long ownerId) {
        return itemRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("item_not_found", "Item not found: " + id));
    }

    private Category getActiveCategoryOrThrow(long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("category_not_found", "Category not found: " + categoryId));
        if (!category.isActive()) {
            throw new BadRequestException("category_inactive", "Category is not active: " + categoryId);
        }
        return category;
    }

    private static ItemDto toDto(Item item) {
        List<ItemImageDto> images = item.getImages().stream()
                .map(ItemService::toImageDto)
                .toList();
        return new ItemDto(
                item.getId(),
                item.getOwner().getId(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getTitle(),
                item.getDescription(),
                item.getEstimatedValue(),
                item.getItemCondition(),
                item.isArchived(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                images
        );
    }

    private static ItemImageDto toImageDto(ItemImage image) {
        return new ItemImageDto(
                image.getId(),
                image.getItem().getId(),
                image.getUrl(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
