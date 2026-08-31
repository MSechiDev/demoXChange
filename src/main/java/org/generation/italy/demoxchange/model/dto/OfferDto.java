package org.generation.italy.demoxchange.model.dto;

import org.generation.italy.demoxchange.model.entities.OfferStatus;

import java.util.List;

public record OfferDto(
    Long offerId,
    Long offererId,
    String offererName,
    List<ItemSummaryDto> offeredItems,
    String message,
    OfferStatus status
) {}
