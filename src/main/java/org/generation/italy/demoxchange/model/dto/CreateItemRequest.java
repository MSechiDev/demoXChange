package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.generation.italy.demoxchange.model.entities.ItemCondition;

import java.math.BigDecimal;

public record CreateItemRequest(
        @NotNull
        Long categoryId,

        @NotBlank
        @Size(max = 120)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @PositiveOrZero
        @Digits(integer = 8, fraction = 2)
        BigDecimal estimatedValue,

        @NotNull
        ItemCondition itemCondition
) {}
