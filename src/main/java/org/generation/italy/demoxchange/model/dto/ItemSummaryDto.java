package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.ItemCondition;

import java.math.BigDecimal;

public record ItemSummaryDto(
        Long id,
        String title,
        ItemCondition condition,
        BigDecimal estimatedValue,
        String imageUrl
) {}