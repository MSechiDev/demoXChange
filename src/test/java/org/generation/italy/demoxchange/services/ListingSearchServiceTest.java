package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ListingSearchDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.repositories.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingSearchServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingSearchService listingSearchService;

    @Test
    void searchListings_mapsListingAndItemFieldsIntoDto() {
        AppUser owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", 1L);

        Category category = new Category("Musica", "musica", null);
        ReflectionTestUtils.setField(category, "id", 10L);

        Item item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(item, "id", 5L);
        item.setEstimatedValue(BigDecimal.valueOf(150));

        Listing listing = new Listing(item, "Cagliari");
        ReflectionTestUtils.setField(listing, "id", 6L);
        listing.setStatus(ListingStatus.attivo);

        when(listingRepository.searchListings(any(), any(), any(), any())).thenReturn(List.of(listing));

        List<ListingSearchDto> result = listingSearchService.searchListings(null, null, null, null);

        assertThat(result).hasSize(1);
        ListingSearchDto dto = result.get(0);
        assertThat(dto.id()).isEqualTo(6L);
        assertThat(dto.city()).isEqualTo("Cagliari");
        assertThat(dto.status()).isEqualTo("attivo");
        assertThat(dto.itemId()).isEqualTo(5L);
        assertThat(dto.itemTitle()).isEqualTo("Chitarra");
        assertThat(dto.itemEstimatedValue()).isEqualByComparingTo("150");
        assertThat(dto.categoryId()).isEqualTo(10L);
        assertThat(dto.categoryName()).isEqualTo("Musica");
    }

    @Test
    void searchListings_forwardsAllFiltersToRepository() {
        when(listingRepository.searchListings("chitarra", 10L, BigDecimal.ONE, BigDecimal.TEN))
                .thenReturn(List.of());

        List<ListingSearchDto> result = listingSearchService.searchListings("chitarra", 10L, BigDecimal.ONE, BigDecimal.TEN);

        assertThat(result).isEmpty();
    }
}
