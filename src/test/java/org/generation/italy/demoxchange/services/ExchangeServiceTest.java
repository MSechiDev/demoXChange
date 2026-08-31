package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ExchangeDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.ExchangeRepository;
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

    @InjectMocks
    private ExchangeService exchangeService;

    private static final long OWNER_ID = 1L;
    private static final long OFFERER_ID = 2L;
    private static final long OUTSIDER_ID = 99L;

    private Exchange exchange;

    @BeforeEach
    void setUp() {
        AppUser owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        AppUser offerer = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(offerer, "id", OFFERER_ID);

        Category category = new Category("Musica", "musica", null);
        Item item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        ReflectionTestUtils.setField(item, "id", 5L);

        Listing listing = new Listing(item, "Cagliari");
        ReflectionTestUtils.setField(listing, "id", 6L);

        Offer offer = new Offer(listing, offerer, offerer);
        ReflectionTestUtils.setField(offer, "id", 3L);

        exchange = new Exchange(offer);
        ReflectionTestUtils.setField(exchange, "id", 1L);
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
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        exchangeService.confirm(1L, OWNER_ID);
        ExchangeDto result = exchangeService.confirm(1L, OFFERER_ID);

        assertThat(result.status()).isEqualTo(ExchangeStatus.completato);
        assertThat(result.ownerConfirmedAt()).isNotNull();
        assertThat(result.offererConfirmedAt()).isNotNull();
        assertThat(result.completedAt()).isNotNull();
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
    void cancel_participant_setsAnnullatoStatus() {
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        ExchangeDto result = exchangeService.cancel(1L, OWNER_ID);

        assertThat(result.status()).isEqualTo(ExchangeStatus.annullato);
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
}
