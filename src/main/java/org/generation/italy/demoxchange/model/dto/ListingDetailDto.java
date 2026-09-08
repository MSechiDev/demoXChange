package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.ItemCondition;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ListingDetailDto(
        Long id,
        String city,
        String status,
        OffsetDateTime publishedAt,
        Long ownerId,
        Long itemId,
        String itemTitle,
        String itemDescription,
        BigDecimal itemEstimatedValue,
        ItemCondition itemCondition,
        Long categoryId,
        String categoryName,
        List<Long> acceptedCategoryIds,
        List<ItemImageDto> images
) {}
