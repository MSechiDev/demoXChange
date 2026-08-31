package org.generation.italy.demoxchange.model.dto;

import java.util.List;

public record CounterOfferRequest(
        List<Long> itemIds,
        String message
) {}