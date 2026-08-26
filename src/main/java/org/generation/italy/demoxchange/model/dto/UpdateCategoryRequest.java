package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank
        @Size(max = 60)
        String name,

        @NotBlank
        @Size(max = 60)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug must be lowercase alphanumeric with single hyphens")
        String slug,

        @Size(max = 255)
        String description,

        boolean active
) {}
