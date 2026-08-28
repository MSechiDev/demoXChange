package org.generation.italy.demoxchange.model.dto;

import java.time.OffsetDateTime;

public record MessageDto(
        long id,
        long offerId,
        long senderId,
        String senderUsername,
        String body,
        OffsetDateTime sentAt,
        OffsetDateTime readAt
) {}
