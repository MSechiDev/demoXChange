package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateReviewRequest;
import org.generation.italy.demoxchange.model.dto.ReviewSummaryDto;
import org.generation.italy.demoxchange.services.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewSummaryDto createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return reviewService.createReview(request, currentUserId(jwt));
    }

    private static long currentUserId(Jwt jwt) {
        return jwt.getClaim("uid");
    }
}
