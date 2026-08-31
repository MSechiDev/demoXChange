package org.generation.italy.demoxchange.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.generation.italy.demoxchange.model.dto.ItemImageDto;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ItemImage;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.ItemImageRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItemImageService {
    private static final int MAX_IMAGES_PER_ITEM = 10;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ImageStorageService imageStorageService;
    private final EntityManager entityManager;

    public ItemImageService(
            ItemRepository itemRepository,
            ItemImageRepository itemImageRepository,
            ImageStorageService imageStorageService,
            EntityManager entityManager
    ) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.imageStorageService = imageStorageService;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ItemImageDto> list(long itemId, long ownerId) {
        getOwnedItemOrThrow(itemId, ownerId);
        return itemImageRepository.findByItemIdOrderByDisplayOrderAsc(itemId).stream()
                .map(ItemImageService::toDto)
                .toList();
    }

    @Transactional
    public ItemImageDto add(long itemId, long ownerId, MultipartFile file) {
        Item item = getOwnedItemOrThrow(itemId, ownerId);

        long current = itemImageRepository.countByItemId(itemId);
        if (current >= MAX_IMAGES_PER_ITEM) {
            throw new ConflictException("too_many_images",
                    "An item can have at most " + MAX_IMAGES_PER_ITEM + " images");
        }

        String url = imageStorageService.store(itemId, file);
        ItemImage image = new ItemImage(item, url, (short) current);
        return toDto(itemImageRepository.save(image));
    }

    @Transactional
    public List<ItemImageDto> reorder(long itemId, long ownerId, List<Long> imageIds) {
        getOwnedItemOrThrow(itemId, ownerId);

        List<ItemImage> existing = itemImageRepository.findByItemIdOrderByDisplayOrderAsc(itemId);
        validateReorder(existing, imageIds);

        Map<Long, ItemImage> byId = existing.stream()
                .collect(Collectors.toMap(ItemImage::getId, Function.identity()));
        List<ItemImage> ordered = imageIds.stream().map(byId::get).toList();
        applyOrder(itemId, ordered);

        return itemImageRepository.findByItemIdOrderByDisplayOrderAsc(itemId).stream()
                .map(ItemImageService::toDto)
                .toList();
    }

    @Transactional
    public void delete(long itemId, long ownerId, long imageId) {
        getOwnedItemOrThrow(itemId, ownerId);

        ItemImage image = itemImageRepository.findByIdAndItemId(imageId, itemId)
                .orElseThrow(() -> new NotFoundException("image_not_found", "Image not found: " + imageId));

        String url = image.getUrl();
        itemImageRepository.delete(image);
        repackDisplayOrder(itemId);
        imageStorageService.delete(url);
    }

    private Item getOwnedItemOrThrow(long itemId, long ownerId) {
        return itemRepository.findByIdAndOwnerId(itemId, ownerId)
                .orElseThrow(() -> new NotFoundException("item_not_found", "Item not found: " + itemId));
    }

    private static void validateReorder(List<ItemImage> existing, List<Long> imageIds) {
        if (imageIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("invalid_image_order", "Image ids must not be null");
        }
        Set<Long> requestedIds = Set.copyOf(imageIds);
        if (requestedIds.size() != imageIds.size()) {
            throw new BadRequestException("invalid_image_order", "Duplicate image ids in the request");
        }
        Set<Long> existingIds = existing.stream().map(ItemImage::getId).collect(Collectors.toSet());
        if (!existingIds.equals(requestedIds)) {
            throw new BadRequestException("invalid_image_order",
                    "The request must list exactly the images of this item");
        }
    }

    private void repackDisplayOrder(long itemId) {
        List<ItemImage> remaining = itemImageRepository.findByItemIdOrderByDisplayOrderAsc(itemId);
        applyOrder(itemId, remaining);
    }

    /**
     * Riscrive display_order = posizione nella lista (0,1,2,...).
     * <p>
     * La tabella ha UNIQUE (item_id, display_order) NON differibile e CHECK 0..9: PostgreSQL
     * verifica il vincolo riga per riga, quindi non si puo' permutare la colonna in un solo
     * UPDATE ne' parcheggiare gli ordini fuori range. La sola via corretta per una permutazione
     * arbitraria e' svuotare le righe dell'item e reinserirle (id e created_at preservati).
     */
    private void applyOrder(long itemId, List<ItemImage> ordered) {
        if (ordered.isEmpty()) {
            return;
        }
        entityManager.createNativeQuery("delete from item_images where item_id = :itemId")
                .setParameter("itemId", itemId)
                .executeUpdate();

        Query insert = entityManager.createNativeQuery("""
                insert into item_images (id, item_id, url, display_order, created_at)
                overriding system value
                values (:id, :itemId, :url, :ord, :createdAt)
                """);
        for (int i = 0; i < ordered.size(); i++) {
            ItemImage img = ordered.get(i);
            insert.setParameter("id", img.getId());
            insert.setParameter("itemId", itemId);
            insert.setParameter("url", img.getUrl());
            insert.setParameter("ord", i);
            insert.setParameter("createdAt", img.getCreatedAt());
            insert.executeUpdate();
        }
        entityManager.clear(); // le entita' caricate ora sono disallineate: scartale
    }

    private static ItemImageDto toDto(ItemImage image) {
        return new ItemImageDto(
                image.getId(),
                image.getItem().getId(),
                image.getUrl(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
