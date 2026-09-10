package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.Size;
import org.generation.italy.demoxchange.model.entities.ExchangeMethod;

public record UpdateExchangeLogisticsRequest(
        @Size(max = 255)
        String location,

        ExchangeMethod method
) {}
