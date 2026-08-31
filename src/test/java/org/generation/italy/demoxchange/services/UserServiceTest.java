package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.UserProfileDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.ReviewRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private ReviewRepository reviewRepository;

    @InjectMocks
    private UserService userService;

    private static final long USER_ID = 1L;

    private AppUser user;
    private AppUser reviewer;

    @BeforeEach
    void setUp() {
        user = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        reviewer = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(reviewer, "id", 2L);
    }

    @Test
    void getProfile_userNotFound_throwsNotFound() {
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getProfile_noReviews_returnsNullAverageAndZeroCount() {
        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(reviewRepository.findByRecipientIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        UserProfileDto result = userService.getProfile(USER_ID);

        assertThat(result.averageRating()).isNull();
        assertThat(result.reviewsCount()).isZero();
        assertThat(result.reviews()).isEmpty();
    }

    @Test
    void getProfile_withReviews_computesAverageRating() {
        Category category = new Category("Musica", "musica", null);
        Item item = new Item(user, category, "Chitarra", "descrizione", ItemCondition.buone);
        Listing listing = new Listing(item, "Cagliari");
        Offer offer = new Offer(listing, reviewer, reviewer);
        Exchange exchange = new Exchange(offer);
        exchange.setStatus(ExchangeStatus.completato);

        Review review1 = new Review(exchange, reviewer, user, (short) 5);
        Review review2 = new Review(exchange, reviewer, user, (short) 3);

        when(appUserRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(reviewRepository.findByRecipientIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(review1, review2));

        UserProfileDto result = userService.getProfile(USER_ID);

        assertThat(result.averageRating()).isEqualTo(4.0);
        assertThat(result.reviewsCount()).isEqualTo(2);
        assertThat(result.reviews()).hasSize(2);
    }
}
