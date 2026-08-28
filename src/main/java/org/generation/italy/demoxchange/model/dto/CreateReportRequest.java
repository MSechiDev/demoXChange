package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotBlank
        @Pattern(regexp = "spam|contenuto_offensivo|truffa|oggetto_illegale|profilo_falso|altro", message = "invalid report reason")
        String reason,

        @Size(max = 1000)
        String description,

        Long reportedUserId,

        Long reportedListingId
) {}
