package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.OfferDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    @Mock private OfferRepository offerRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ExchangeRepository exchangeRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private OfferService offerService;

    private static final long OWNER_ID = 1L;
    private static final long OFFERER_ID = 2L;

    private Listing listing;
    private Offer offer;

    @BeforeEach
    void setUp() {
        AppUser owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        AppUser offerer = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(offerer, "id", OFFERER_ID);

        Category category = new Category("Musica", "musica", null);
        Item item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(item, "id", 5L);

        listing = new Listing(item, "Cagliari");
        ReflectionTestUtils.setField(listing, "id", 6L);

        offer = new Offer(listing, offerer, offerer);
        ReflectionTestUtils.setField(offer, "id", 3L);
    }

    @Test
    void approveOffer_setsAcceptedStatusAndRespondedAt() {
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));
        when(offerRepository.findByListingIdAndStatus(6L, OfferStatus.in_attesa)).thenReturn(List.of());

        OfferDto result = offerService.approveOffer(3L, OWNER_ID);

        assertThat(result.status()).isEqualTo(OfferStatus.accettata);
        assertThat(offer.getRespondedAt()).isNotNull();

        ArgumentCaptor<Exchange> exchangeCaptor = ArgumentCaptor.forClass(Exchange.class);
        verify(exchangeRepository).save(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue().getOffer()).isSameAs(offer);
    }

    @Test
    void approveOffer_rejectsOtherPendingOffersWithRespondedAtSet() {
        AppUser otherOfferer = new AppUser("carol", "hash", null);
        ReflectionTestUtils.setField(otherOfferer, "id", 3L);
        Offer competingOffer = new Offer(listing, otherOfferer, otherOfferer);
        ReflectionTestUtils.setField(competingOffer, "id", 4L);

        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));
        when(offerRepository.findByListingIdAndStatus(6L, OfferStatus.in_attesa))
                .thenReturn(List.of(offer, competingOffer));

        offerService.approveOffer(3L, OWNER_ID);

        assertThat(competingOffer.getStatus()).isEqualTo(OfferStatus.rifiutata);
        assertThat(competingOffer.getRespondedAt()).isNotNull();
    }

    @Test
    void approveOffer_callerNotListingOwner_throwsForbidden() {
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.approveOffer(3L, OFFERER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(exchangeRepository, never()).save(any());
    }

    @Test
    void approveOffer_offerNotPending_throwsBadRequest() {
        offer.setStatus(OfferStatus.rifiutata);
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.approveOffer(3L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);

        verify(exchangeRepository, never()).save(any());
    }

    @Test
    void counterOffer_setsParentStatusToControproposta() {
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(offer.getListing().getItem().getOwner()));
        when(itemRepository.findAllById(List.of(5L))).thenReturn(List.of(offer.getListing().getItem()));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        offerService.counterOffer(3L, List.of(5L), "controproposta", OWNER_ID);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.controproposta);
        assertThat(offer.getRespondedAt()).isNotNull();
    }

    @Test
    void counterOffer_createsChildOfferInAttesaLinkedToParent() {
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));
        when(appUserRepository.findById(OWNER_ID)).thenReturn(Optional.of(offer.getListing().getItem().getOwner()));
        when(itemRepository.findAllById(List.of(5L))).thenReturn(List.of(offer.getListing().getItem()));

        ArgumentCaptor<Offer> counterCaptor = ArgumentCaptor.forClass(Offer.class);
        when(offerRepository.save(counterCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        offerService.counterOffer(3L, List.of(5L), "controproposta", OWNER_ID);

        Offer counter = counterCaptor.getValue();
        assertThat(counter.getStatus()).isEqualTo(OfferStatus.in_attesa);
        assertThat(counter.getParentOffer()).isSameAs(offer);
    }

    @Test
    void counterOffer_parentNotPending_throwsBadRequest() {
        offer.setStatus(OfferStatus.rifiutata);
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.counterOffer(3L, List.of(5L), "msg", OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void counterOffer_callerNotListingOwner_throwsForbidden() {
        when(offerRepository.findById(3L)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerService.counterOffer(3L, List.of(5L), "msg", OFFERER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
