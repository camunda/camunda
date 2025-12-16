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
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(
    name = "IncidentStateFilter",
    description = "Incident state filter with different filter operations (can be combined).")
public record IncidentStateFilter(
    @Nullable
        @Schema(
            name = "eq",
            description = "Checks for equality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        IncidentState eq,
    @Nullable
        @Schema(
            description = "Checks for inequality with the provided value.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        IncidentState neq,
    @Nullable
        @Valid
        @Schema(
            description = "Checks if the property matches any of the provided values.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull IncidentState> in,
    @Nullable
        @Valid
        @Schema(
            description = "Checks if the property matches none of the provided values.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotNull IncidentState> notIn) {}
