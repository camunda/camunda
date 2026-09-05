/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.businessvalue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for {@code PUT /business-value/targets/{tenantId}/{processKey}}. Either field may be
 * {@code null} — that clears the corresponding KPI's target. Both being {@code null} is a valid
 * "clear both" upsert.
 *
 * <p>Unknown top-level properties (notably a future {@code volumeTarget}) are silently dropped by
 * Optimize's shared {@code ObjectMapper} configuration, matching the codebase convention for every
 * other REST body — no per-DTO strict-mode override.
 */
public record BusinessValueTargetUpsertRequestDto(
    @Valid CycleTimeTargetDto cycleTimeTarget,
    @Min(value = 0, message = "automationRateTargetPct must be within [0, 100]")
        @Max(value = 100, message = "automationRateTargetPct must be within [0, 100]")
        Integer automationRateTargetPct) {}
