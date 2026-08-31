package org.generation.italy.demoxchange.model.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateListingRequest(
        @NotBlank
        @Size(max = 120)
        String city,

        @NotNull
        List<Long> acceptedCategoryIds,


        @NotNull
        Long itemId
) {}
