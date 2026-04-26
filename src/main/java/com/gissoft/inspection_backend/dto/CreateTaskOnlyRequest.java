package com.gissoft.inspection_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record CreateTaskOnlyRequest(

        // Entity
        @NotBlank String entityName,
        @NotBlank String ownerName,
        String ownerPhone,
        @NotBlank String directorate,
        @NotBlank String category,
        Double lat,
        Double lon,

        // Task
        OffsetDateTime dueAt,
        String priority
) {}