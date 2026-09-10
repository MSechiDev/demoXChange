package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ExchangeDto;
import org.generation.italy.demoxchange.model.dto.UpdateExchangeLogisticsRequest;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.ExchangeRepository;
import org.generation.italy.demoxchange.model.repositories.ReviewRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeRepository exchangeRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private ExchangeService exchangeService;

    private static final long OWNER_ID = 1L;
    private static final long OFFERER_ID = 2L;
    private static final long OUTSIDER_ID = 99L;

    private Exchange exchange;
    private Listing listing;

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
        listing.setStatus(ListingStatus.in_trattativa);

        Offer offer = new Offer(listing, offerer, offerer);
        ReflectionTestUtils.setField(offer, "id", 3L);

        exchange = new Exchange(offer);
        ReflectionTestUtils.setField(exchange, "id", 1L);
        exchange.setLocation("Piazza Duomo");
        exchange.setMethod(ExchangeMethod.di_persona);
        exchange.setLogisticsConfirmedByOwner(true);
        exchange.setLogisticsConfirmedByOfferer(true);
    }

    @Test
    void confirm_firstConfirmationByOwner_onlySetsOwnerConfirmedAt() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        ExchangeDto result = exchangeService.confirm(1L, OWNER_ID);

        assertThat(result.ownerConfirmedAt()).isNotNull();
        assertThat(result.offererConfirmedAt()).isNull();
        assertThat(result.status()).isEqualTo(ExchangeStatus.in_corso);
        assertThat(result.completedAt()).isNull();
    }

    @Test
    void confirm_bothPartiesConfirm_completesExchange() {
        Category otherCategory = new Category("Sport", "sport", null);
        Item offeredItem = new Item(exchange.getOffer().getOfferer(), otherCategory, "Racchetta", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(offeredItem, "id", 8L);
        exchange.getOffer().getItems().add(offeredItem);

        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        exchangeService.confirm(1L, OWNER_ID);
        ExchangeDto result = exchangeService.confirm(1L, OFFERER_ID);

        assertThat(result.status()).isEqualTo(ExchangeStatus.completato);
        assertThat(result.ownerConfirmedAt()).isNotNull();
        assertThat(result.offererConfirmedAt()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.scambiato);
        assertThat(listing.getItem().isArchived()).isTrue();
        assertThat(offeredItem.isArchived()).isTrue();
    }

    @Test
    void confirm_exchangeFromCounterOffer_archivesItemsFromWholeChain() {
        AppUser bidder = exchange.getOffer().getOfferer();
        AppUser owner = listing.getItem().getOwner();
        Offer parentOffer = exchange.getOffer();

        Category otherCategory = new Category("Sport", "sport", null);
        Item bidderItem = new Item(bidder, otherCategory, "Racchetta", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(bidderItem, "id", 8L);
        parentOffer.getItems().add(bidderItem);

        Item counterItem = new Item(owner, otherCategory, "Pallone", "descrizione", ItemCondition.nuovo);
        ReflectionTestUtils.setField(counterItem, "id", 9L);
        Offer counterOffer = new Offer(listing, bidder, owner);
        ReflectionTestUtils.setField(counterOffer, "id", 4L);
        counterOffer.setParentOffer(parentOffer);
        counterOffer.getItems().add(counterItem);

        Exchange counterExchange = new Exchange(counterOffer);
        ReflectionTestUtils.setField(counterExchange, "id", 2L);
        counterExchange.setLocation("Piazza Duomo");
        counterExchange.setMethod(ExchangeMethod.di_persona);
        counterExchange.setLogisticsConfirmedByOwner(true);
        counterExchange.setLogisticsConfirmedByOfferer(true);

        when(exchangeRepository.findById(2L)).thenReturn(Optional.of(counterExchange));

        exchangeService.confirm(2L, OWNER_ID);
        exchangeService.confirm(2L, OFFERER_ID);

        assertThat(listing.getItem().isArchived()).isTrue();
        assertThat(bidderItem.isArchived()).isTrue();
        assertThat(counterItem.isArchived()).isTrue();
    }

    @Test
    void confirm_sameUserTwice_throwsConflict() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        exchangeService.confirm(1L, OWNER_ID);

        assertThatThrownBy(() -> exchangeService.confirm(1L, OWNER_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirm_userNotPartOfExchange_throwsForbidden() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirm(1L, OUTSIDER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void confirm_exchangeAlreadyCompleted_throwsBadRequest() {
        exchange.setStatus(ExchangeStatus.completato);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirm(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void confirm_exchangeNotFound_throwsNotFound() {
        when(exchangeRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exchangeService.confirm(404L, OWNER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void confirm_locationOrMethodNotSet_throwsBadRequest() {
        exchange.setLocation(null);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirm(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void confirm_logisticsNotConfirmedByBothParties_throwsBadRequest() {
        exchange.setLogisticsConfirmedByOfferer(false);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirm(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancel_participant_setsAnnullatoStatusAndReactivatesListing() {
        exchange.getOffer().setStatus(OfferStatus.accettata);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        ExchangeDto result = exchangeService.cancel(1L, OWNER_ID);

        assertThat(result.status()).isEqualTo(ExchangeStatus.annullato);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.attivo);
        assertThat(exchange.getOffer().getStatus()).isEqualTo(OfferStatus.annullata);
    }

    @Test
    void cancel_nonParticipant_throwsForbidden() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.cancel(1L, OUTSIDER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cancel_alreadyCompleted_throwsBadRequest() {
        exchange.setStatus(ExchangeStatus.completato);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.cancel(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void isParticipant_ownerOrOfferer_returnsTrue() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThat(exchangeService.isParticipant(1L, OWNER_ID)).isTrue();
        assertThat(exchangeService.isParticipant(1L, OFFERER_ID)).isTrue();
    }

    @Test
    void isParticipant_outsider_returnsFalse() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThat(exchangeService.isParticipant(1L, OUTSIDER_ID)).isFalse();
    }

    @Test
    void findById_reflectsWhetherViewerAlreadyReviewed() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        when(reviewRepository.existsByExchangeIdAndAuthorId(1L, OWNER_ID)).thenReturn(true);
        when(reviewRepository.existsByExchangeIdAndAuthorId(1L, OFFERER_ID)).thenReturn(false);

        assertThat(exchangeService.findById(1L, OWNER_ID).reviewedByMe()).isTrue();
        assertThat(exchangeService.findById(1L, OFFERER_ID).reviewedByMe()).isFalse();
    }

    @Test
    void updateLogistics_bothFieldsNull_throwsBadRequest() {
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest(null, null);

        assertThatThrownBy(() -> exchangeService.updateLogistics(1L, OWNER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateLogistics_exchangeNotInCorso_throwsBadRequest() {
        exchange.setStatus(ExchangeStatus.completato);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest("Piazza Duomo", null);

        assertThatThrownBy(() -> exchangeService.updateLogistics(1L, OWNER_ID, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateLogistics_nonParticipant_throwsForbidden() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest("Piazza Duomo", null);

        assertThatThrownBy(() -> exchangeService.updateLogistics(1L, OUTSIDER_ID, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateLogistics_participantSetsBothFields_succeeds() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest("Piazza Duomo", ExchangeMethod.di_persona);

        ExchangeDto result = exchangeService.updateLogistics(1L, OFFERER_ID, request);

        assertThat(result.location()).isEqualTo("Piazza Duomo");
        assertThat(result.method()).isEqualTo(ExchangeMethod.di_persona);
    }

    @Test
    void updateLogistics_partialUpdate_leavesOtherFieldUntouched() {
        exchange.setLocation("Piazza Duomo");
        exchange.setMethod(ExchangeMethod.di_persona);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest(null, ExchangeMethod.spedizione);

        ExchangeDto result = exchangeService.updateLogistics(1L, OWNER_ID, request);

        assertThat(result.location()).isEqualTo("Piazza Duomo");
        assertThat(result.method()).isEqualTo(ExchangeMethod.spedizione);
    }

    @Test
    void updateLogistics_successfulChange_resetsBothConfirmationFlags() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        UpdateExchangeLogisticsRequest request = new UpdateExchangeLogisticsRequest("Nuova Piazza", null);

        ExchangeDto result = exchangeService.updateLogistics(1L, OWNER_ID, request);

        assertThat(result.logisticsConfirmedByOwner()).isFalse();
        assertThat(result.logisticsConfirmedByOfferer()).isFalse();
    }

    @Test
    void confirmLogistics_ownerConfirms_setsOnlyOwnerFlag() {
        exchange.setLogisticsConfirmedByOwner(false);
        exchange.setLogisticsConfirmedByOfferer(false);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        ExchangeDto result = exchangeService.confirmLogistics(1L, OWNER_ID);

        assertThat(result.logisticsConfirmedByOwner()).isTrue();
        assertThat(result.logisticsConfirmedByOfferer()).isFalse();
    }

    @Test
    void confirmLogistics_offererConfirms_setsOnlyOffererFlag() {
        exchange.setLogisticsConfirmedByOwner(false);
        exchange.setLogisticsConfirmedByOfferer(false);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        ExchangeDto result = exchangeService.confirmLogistics(1L, OFFERER_ID);

        assertThat(result.logisticsConfirmedByOwner()).isFalse();
        assertThat(result.logisticsConfirmedByOfferer()).isTrue();
    }

    @Test
    void confirmLogistics_calledAgain_isIdempotent() {
        exchange.setLogisticsConfirmedByOwner(false);
        exchange.setLogisticsConfirmedByOfferer(false);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        exchangeService.confirmLogistics(1L, OWNER_ID);
        ExchangeDto result = exchangeService.confirmLogistics(1L, OWNER_ID);

        assertThat(result.logisticsConfirmedByOwner()).isTrue();
    }

    @Test
    void confirmLogistics_locationOrMethodNotSet_throwsBadRequestWithLogisticsNotSetCode() {
        exchange.setLocation(null);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirmLogistics(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getErrorCode()).isEqualTo("logistics_not_set"));
    }

    @Test
    void confirmLogistics_nonParticipant_throwsForbidden() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirmLogistics(1L, OUTSIDER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void confirmLogistics_notInCorso_throwsBadRequest() {
        exchange.setStatus(ExchangeStatus.completato);
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        assertThatThrownBy(() -> exchangeService.confirmLogistics(1L, OWNER_ID))
                .isInstanceOf(BadRequestException.class);
    }
}
