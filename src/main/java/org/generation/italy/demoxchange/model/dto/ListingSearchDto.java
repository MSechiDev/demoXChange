package org.generation.italy.demoxchange.model.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ListingSearchDto(
        Long id,
        String city,
        String status,
        OffsetDateTime publishedAt,
        Long itemId,
        String itemTitle,
        String itemDescription,
        BigDecimal itemEstimatedValue,
        Long categoryId,
        String categoryName
) {}