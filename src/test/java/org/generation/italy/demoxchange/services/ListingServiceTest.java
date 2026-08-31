package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateListingRequest;
import org.generation.italy.demoxchange.model.dto.ListingDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.CategoryRepository;
import org.generation.italy.demoxchange.model.repositories.ItemRepository;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ListingRepository listingRepository;

    @InjectMocks
    private ListingService listingService;

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

        item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(item, "id", 5L);
    }

    @Test
    void createListing_itemNotOwnedByCaller_throwsNotFound() {
        AppUser other = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(other, "id", 2L);
        Item othersItem = new Item(other, category, "Cuffie", "descrizione", ItemCondition.nuovo);
        ReflectionTestUtils.setField(othersItem, "id", 6L);

        CreateListingRequest request = new CreateListingRequest("Cagliari", List.of(10L), 6L);
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(6L)).thenReturn(Optional.of(othersItem));

        assertThatThrownBy(() -> listingService.createListing(OWNER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createListing_itemAlreadyListed_throwsConflict() {
        CreateListingRequest request = new CreateListingRequest("Cagliari", List.of(10L), 5L);
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(listingRepository.existsByItemId(5L)).thenReturn(true);

        assertThatThrownBy(() -> listingService.createListing(OWNER_ID, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createListing_noAcceptedCategories_throwsBadRequest() {
        CreateListingRequest request = new CreateListingRequest("Cagliari", List.of(), 5L);
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(listingRepository.existsByItemId(5L)).thenReturn(false);

        assertThatThrownBy(() -> listingService.createListing(OWNER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createListing_unknownCategory_throwsNotFound() {
        CreateListingRequest request = new CreateListingRequest("Cagliari", List.of(10L, 99L), 5L);
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(listingRepository.existsByItemId(5L)).thenReturn(false);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));

        assertThatThrownBy(() -> listingService.createListing(OWNER_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createListing_validRequest_createsActiveListing() {
        CreateListingRequest request = new CreateListingRequest("Cagliari", List.of(10L), 5L);
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(listingRepository.existsByItemId(5L)).thenReturn(false);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListingDto result = listingService.createListing(OWNER_ID, request);

        assertThat(result.city()).isEqualTo("Cagliari");
        assertThat(result.status()).isEqualTo(ListingStatus.attivo);
        assertThat(result.acceptedCategoryIds()).containsExactly(10L);
    }

    @Test
    void updateStatus_nullStatus_throwsBadRequest() {
        assertThatThrownBy(() -> listingService.updateStatus(OWNER_ID, 6L, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateStatus_notOwner_throwsNotFound() {
        Listing listing = new Listing(item, "Cagliari");
        ReflectionTestUtils.setField(listing, "id", 6L);
        when(listingRepository.findById(6L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateStatus(99L, 6L, ListingStatus.scambiato))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateStatus_owner_updatesStatus() {
        Listing listing = new Listing(item, "Cagliari");
        ReflectionTestUtils.setField(listing, "id", 6L);
        when(listingRepository.findById(6L)).thenReturn(Optional.of(listing));
        when(listingRepository.saveAndFlush(listing)).thenReturn(listing);

        ListingDto result = listingService.updateStatus(OWNER_ID, 6L, ListingStatus.scambiato);

        assertThat(result.status()).isEqualTo(ListingStatus.scambiato);
    }

    @Test
    void findByOwner_userNotFound_throwsNotFound() {
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.findByOwner(OWNER_ID))
                .isInstanceOf(NotFoundException.class);
    }
}
