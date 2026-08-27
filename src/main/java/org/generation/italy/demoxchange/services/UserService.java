package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.ReviewSummaryDto;
import org.generation.italy.demoxchange.model.dto.UserProfileDto;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Review;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final AppUserRepository appUserRepository;
    private final ReviewRepository reviewRepository;

    public UserService(AppUserRepository appUserRepository, ReviewRepository reviewRepository) {
        this.appUserRepository = appUserRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(long userId) {
        AppUser user = getOrThrow(userId);

        List<Review> reviews = reviewRepository.findByRecipientIdOrderByCreatedAtDesc(userId);

        Double averageRating = reviews.isEmpty()
                ? null
                : reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        List<ReviewSummaryDto> reviewDtos = reviews.stream()
                .map(UserService::toReviewSummaryDto)
                .toList();

        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                averageRating,
                reviews.size(),
                reviewDtos
        );
    }

    private AppUser getOrThrow(long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("user_not_found", "User not found: " + id));
    }

    private static ReviewSummaryDto toReviewSummaryDto(Review review) {
        return new ReviewSummaryDto(
                review.getId(),
                review.getAuthor().getId(),
                review.getAuthor().getUsername(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}