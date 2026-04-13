package com.gissoft.inspection_backend.dto;

import java.time.OffsetDateTime;

public record TimelineDto(
        String phase,
        String status,
        String assignedTo,
        String completedBy,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt
) {}