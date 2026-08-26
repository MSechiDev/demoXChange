package org.generation.italy.demoxchange.model.dto;

import java.time.OffsetDateTime;

public record CategoryDto(
        long id,
        String name,
        String slug,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
