package org.generation.italy.demoxchange.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewReportRequest(
        @NotBlank
        @Pattern(regexp = "in_revisione|risolta|respinta", message = "status must be in_revisione, risolta or respinta")
        String status,

        @Size(max = 1000)
        String resolutionNote
) {}
