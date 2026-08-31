package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateReviewDto;
import org.generation.italy.demoxchange.model.dto.ReviewSummaryDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ExchangeRepository exchangeRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private ReviewService reviewService;

    private AppUser owner;
    private AppUser offerer;
    private Exchange exchange;

    @BeforeEach
    void setUp() {
        owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", 1L);

        offerer = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(offerer, "id", 2L);

        Category category = new Category("Musica", "musica", null);
        Item item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        Listing listing = new Listing(item, "Cagliari");
        Offer offer = new Offer(listing, offerer, offerer);
        ReflectionTestUtils.setField(offer, "id", 3L);

        exchange = new Exchange(offer);
        ReflectionTestUtils.setField(exchange, "id", 1L);
        exchange.setStatus(ExchangeStatus.completato);
    }

    @Test
    void createReview_exchangeNotCompleted_throwsBadRequest() {
        exchange.setStatus(ExchangeStatus.in_corso);
        when(appUserRepository.findByUsername("bob")).thenReturn(Optional.of(offerer));
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        CreateReviewDto dto = new CreateReviewDto(1L, (short) 5, "ottimo");

        assertThatThrownBy(() -> reviewService.createReview(dto, "bob"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createReview_authorNotPartOfExchange_throwsForbidden() {
        AppUser outsider = new AppUser("carol", "hash", null);
        ReflectionTestUtils.setField(outsider, "id", 99L);

        when(appUserRepository.findByUsername("carol")).thenReturn(Optional.of(outsider));
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

        CreateReviewDto dto = new CreateReviewDto(1L, (short) 5, "ottimo");

        assertThatThrownBy(() -> reviewService.createReview(dto, "carol"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createReview_duplicateReview_throwsConflict() {
        when(appUserRepository.findByUsername("bob")).thenReturn(Optional.of(offerer));
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        when(reviewRepository.existsByExchangeIdAndAuthorId(1L, 2L)).thenReturn(true);

        CreateReviewDto dto = new CreateReviewDto(1L, (short) 5, "ottimo");

        assertThatThrownBy(() -> reviewService.createReview(dto, "bob"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReview_offererReviewsOwner_recipientIsOwner() {
        when(appUserRepository.findByUsername("bob")).thenReturn(Optional.of(offerer));
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        when(reviewRepository.existsByExchangeIdAndAuthorId(1L, 2L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateReviewDto dto = new CreateReviewDto(1L, (short) 5, "ottimo scambio");
        ReviewSummaryDto result = reviewService.createReview(dto, "bob");

        assertThat(result.authorUsername()).isEqualTo("bob");
        assertThat(result.rating()).isEqualTo((short) 5);
    }

    @Test
    void createReview_ownerReviewsOfferer_recipientIsOfferer() {
        when(appUserRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
        when(reviewRepository.existsByExchangeIdAndAuthorId(1L, 1L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateReviewDto dto = new CreateReviewDto(1L, (short) 4, "buono scambio");
        ReviewSummaryDto result = reviewService.createReview(dto, "alice");

        assertThat(result.authorUsername()).isEqualTo("alice");
        assertThat(result.rating()).isEqualTo((short) 4);
    }
}
