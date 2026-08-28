package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewDto(
        @NotNull(message = "L'ID dello scambio è obbligatorio")
        Long exchangeId,

        @NotNull(message = "La valutazione è obbligatoria")
        @Min(value = 1, message = "La valutazione minima è 1")
        @Max(value = 5, message = "La valutazione massima è 5")
        Short rating,

        @Size(max = 1000, message = "Il commento non può superare 1000 caratteri")
        String comment
) {}