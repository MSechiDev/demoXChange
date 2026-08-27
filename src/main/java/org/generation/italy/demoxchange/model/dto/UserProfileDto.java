package org.generation.italy.demoxchange.model.dto;

import java.util.List;

public record UserProfileDto(
        Long userId,
        String username,
        Double averageRating,
        long reviewsCount,
        List<ReviewSummaryDto> reviews
) {}