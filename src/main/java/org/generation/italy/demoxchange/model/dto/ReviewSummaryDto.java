package org.generation.italy.demoxchange.model.dto;

import java.time.OffsetDateTime;

public record ReviewSummaryDto(
        Long id,
        Long authorId,
        String authorUsername,
        Short rating,
        String comment,
        OffsetDateTime createdAt
) {}