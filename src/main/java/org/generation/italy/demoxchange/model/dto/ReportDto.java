package org.generation.italy.demoxchange.model.dto;

import java.time.OffsetDateTime;

public record ReportDto(
        long id,
        long reporterId,
        String reporterUsername,
        Long reportedUserId,
        String reportedUsername,
        Long reportedListingId,
        String reason,
        String description,
        String status,
        Long reviewedById,
        String reviewedByUsername,
        OffsetDateTime reviewedAt,
        String resolutionNote,
        OffsetDateTime createdAt
) {}
