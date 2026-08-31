package org.generation.italy.demoxchange.model.dto;

import java.time.OffsetDateTime;

public record ItemImageDto(
        long id,
        long itemId,
        String url,
        short displayOrder,
        OffsetDateTime createdAt
) {}
