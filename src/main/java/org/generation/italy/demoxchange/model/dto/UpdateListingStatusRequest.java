package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotNull;
import org.generation.italy.demoxchange.model.entities.ListingStatus;

public record UpdateListingStatusRequest(
        @NotNull
        ListingStatus status
) {}
