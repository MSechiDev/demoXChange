package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderImagesRequest(
        @NotEmpty
        List<Long> imageIds
) {}
