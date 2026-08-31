package org.generation.italy.demoxchange.controllers;

import jakarta.validation.Valid;
import org.generation.italy.demoxchange.model.dto.CreateReviewDto;
import org.generation.italy.demoxchange.model.dto.ReviewSummaryDto;
import org.generation.italy.demoxchange.services.ReviewService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ReviewSummaryDto> createReview(
            @Valid @RequestBody CreateReviewDto dto,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // Jwt legge il campo 'sub' (subject) che contiene lo username dell'utente loggato
        String username = jwt.getSubject();
        ReviewSummaryDto response = reviewService.createReview(dto, username);
        return ResponseEntity.ok(response);
    }
}