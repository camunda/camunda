/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(
    name = "DateTimeFilter",
    description =
        "Filter date-time values with different filter operations (can be combined). All date-time values must be in RFC 3339 format.")
public record DateTimeFilter(
    @Nullable
        @Schema(
            name = "eq",
            description = "Checks for equality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime eq,
    @Nullable
        @Schema(
            description = "Checks for inequality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime neq,
    @Nullable
        @Schema(
            description = "Greater than comparison with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime gt,
    @Nullable
        @Schema(
            description = "Greater than or equal comparison with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime gte,
    @Nullable
        @Schema(
            description = "Lower than comparison with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime lt,
    @Nullable
        @Schema(
            description = "Lower than or equal comparison with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        OffsetDateTime lte,
    @Nullable
        @Valid
        @Schema(
            description = "Checks if the property matches any of the provided values.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<OffsetDateTime> in) {}
