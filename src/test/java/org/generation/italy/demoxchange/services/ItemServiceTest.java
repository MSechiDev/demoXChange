package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateItemRequest;
import org.generation.italy.demoxchange.model.dto.ItemDto;
import org.generation.italy.demoxchange.model.dto.UpdateItemRequest;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Category;
import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ItemCondition;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock private ItemRepository itemRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private ItemService itemService;

    private static final long OWNER_ID = 1L;

    private AppUser owner;
    private Category category;
    private Item item;

    @BeforeEach
    void setUp() {
        owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        category = new Category("Musica", "musica", null);
        ReflectionTestUtils.setField(category, "id", 10L);
        category.setActive(true);

        item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(item, "id", 5L);
    }

    @Test
    void search_minValueGreaterThanMaxValue_throwsBadRequest() {
        assertThatThrownBy(() -> itemService.search(OWNER_ID, null, null, BigDecimal.TEN, BigDecimal.ONE, false, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void findByIdForOwner_notOwned_throwsNotFound() {
        when(itemRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.findByIdForOwner(5L, OWNER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_categoryInactive_throwsBadRequest() {
        category.setActive(false);
        CreateItemRequest request = new CreateItemRequest(10L, "Chitarra", "descrizione", BigDecimal.TEN, ItemCondition.buone);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> itemService.create(OWNER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_categoryNotFound_throwsNotFound() {
        CreateItemRequest request = new CreateItemRequest(10L, "Chitarra", "descrizione", BigDecimal.TEN, ItemCondition.buone);
        when(categoryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.create(OWNER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_validRequest_savesItem() {
        CreateItemRequest request = new CreateItemRequest(10L, "  Chitarra  ", "  descrizione  ", BigDecimal.TEN, ItemCondition.buone);
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 7L);
            return saved;
        });

        ItemDto result = itemService.create(OWNER_ID, request);

        assertThat(result.title()).isEqualTo("Chitarra");
        assertThat(result.description()).isEqualTo("descrizione");
    }

    @Test
    void update_notOwned_throwsNotFound() {
        UpdateItemRequest request = new UpdateItemRequest(10L, "Nuovo titolo", "nuova descrizione", BigDecimal.ONE, ItemCondition.ottime, false);
        when(itemRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.update(5L, OWNER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_validRequest_updatesFields() {
        UpdateItemRequest request = new UpdateItemRequest(10L, "Nuovo titolo", "nuova descrizione", BigDecimal.ONE, ItemCondition.ottime, true);
        when(itemRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(item));
        when(itemRepository.saveAndFlush(item)).thenReturn(item);

        ItemDto result = itemService.update(5L, OWNER_ID, request);

        assertThat(result.title()).isEqualTo("Nuovo titolo");
        assertThat(result.archived()).isTrue();
        assertThat(result.itemCondition()).isEqualTo(ItemCondition.ottime);
    }

    @Test
    void delete_archivesItemInsteadOfRemoving() {
        when(itemRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(item));

        itemService.delete(5L, OWNER_ID);

        assertThat(item.isArchived()).isTrue();
    }
}
