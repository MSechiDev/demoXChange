package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.ListingStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ListingDto(
        Long id,
        Long itemId,
        Long ownerId,
        String city,
        ListingStatus status,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt,
        List<Long> acceptedCategoryIds
) {}
