package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.springframework.stereotype.Service;

@Service
public class ItemService {
    private final AppUserRepository appUserRepository;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    public ItemService(AppUserRepository appUserRepository, CategoryRepository categoryRepository, ItemRepository itemRepository) {
        this.appUserRepository = appUserRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    public Item create(Long ownerId, CreateItemRequest request) {
        AppUser owner = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));

        Item item = new Item(
                owner,
                category,
                request.title(),
                request.description(),
                request.itemCondition()

        );
        item.setEstimatedValue(request.estimatedValue());
        return itemRepository.save(item);
    }

}