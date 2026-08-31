package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.ExchangeStatus;

import java.time.OffsetDateTime;

public record ExchangeDto(
        long id,
        long offerId,
        long listingId,
        long ownerId,
        long offererId,
        ExchangeStatus status,
        OffsetDateTime ownerConfirmedAt,
        OffsetDateTime offererConfirmedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {}
