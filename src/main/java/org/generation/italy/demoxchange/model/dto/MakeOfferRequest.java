package org.generation.italy.demoxchange.model.dto;

import java.util.List;

public record MakeOfferRequest(
        List<Long> itemIds,
        String message
) {}
