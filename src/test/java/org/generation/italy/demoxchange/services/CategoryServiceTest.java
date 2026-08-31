package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CategoryDto;
import org.generation.italy.demoxchange.model.dto.CreateCategoryRequest;
import org.generation.italy.demoxchange.model.dto.UpdateCategoryRequest;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category("Musica", "musica", "Strumenti musicali");
        ReflectionTestUtils.setField(category, "id", 1L);
    }

    @Test
    void findById_notFound_throwsNotFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_nameAlreadyTaken_throwsConflict() {
        CreateCategoryRequest request = new CreateCategoryRequest("Musica", "musica-2", null);
        when(categoryRepository.existsByNameIgnoreCase("Musica")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_slugAlreadyTaken_throwsConflict() {
        CreateCategoryRequest request = new CreateCategoryRequest("Sport", "musica", null);
        when(categoryRepository.existsByNameIgnoreCase("Sport")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("musica")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_validRequest_savesCategory() {
        CreateCategoryRequest request = new CreateCategoryRequest("Sport", "sport", "Attrezzatura sportiva");
        when(categoryRepository.existsByNameIgnoreCase("Sport")).thenReturn(false);
        when(categoryRepository.existsBySlugIgnoreCase("sport")).thenReturn(false);
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(invocation -> {
                    Category saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 2L);
                    return saved;
                });

        CategoryDto result = categoryService.create(request);

        assertThat(result.name()).isEqualTo("Sport");
        assertThat(result.slug()).isEqualTo("sport");
    }

    @Test
    void update_nameTakenByAnotherCategory_throwsConflict() {
        UpdateCategoryRequest request = new UpdateCategoryRequest("Elettronica", "musica", null, true);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Elettronica", 1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(1L, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void setActive_deactivatesCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(category)).thenReturn(category);

        CategoryDto result = categoryService.setActive(1L, false);

        assertThat(result.active()).isFalse();
    }
}
