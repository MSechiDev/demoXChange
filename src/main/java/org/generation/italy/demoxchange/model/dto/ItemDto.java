package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.ItemCondition;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ItemDto(
        long id,
        long ownerId,
        long categoryId,
        String categoryName,
        String title,
        String description,
        BigDecimal estimatedValue,
        ItemCondition itemCondition,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
