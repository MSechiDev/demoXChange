package org.generation.italy.demoxchange.model.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

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
        String categoryName,
        List<Long> acceptedCategoryIds,
        List<String> acceptedCategoryNames,
        String primaryImageUrl
) {}