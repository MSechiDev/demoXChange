package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CategoryDto;
import org.generation.italy.demoxchange.model.dto.CreateCategoryRequest;
import org.generation.italy.demoxchange.model.dto.UpdateCategoryRequest;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> findAll() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(CategoryService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto findById(long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public CategoryDto create(CreateCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("category_name_taken", "A category with this name already exists: " + request.name());
        }
        if (categoryRepository.existsBySlugIgnoreCase(request.slug())) {
            throw new ConflictException("category_slug_taken", "A category with this slug already exists: " + request.slug());
        }

        Category category = new Category(request.name(), request.slug(), request.description());
        Category saved = categoryRepository.save(category);
        return toDto(saved);
    }

    @Transactional
    public CategoryDto update(long id, UpdateCategoryRequest request) {
        Category category = getOrThrow(id);

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("category_name_taken", "A category with this name already exists: " + request.name());
        }
        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(request.slug(), id)) {
            throw new ConflictException("category_slug_taken", "A category with this slug already exists: " + request.slug());
        }

        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());
        category.setActive(request.active());

        Category flushed = categoryRepository.saveAndFlush(category);
        return toDto(flushed);
    }

    private Category getOrThrow(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("category_not_found", "Category not found: " + id));
    }

    private static CategoryDto toDto(Category category) {
        return new CategoryDto(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
