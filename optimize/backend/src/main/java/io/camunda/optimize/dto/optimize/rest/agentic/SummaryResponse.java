/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.agentic;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Response for GET /api/agentic-control-plane/summary. */
@NullMarked
public record SummaryResponse(
    long totalRuns,
    @Nullable Long totalRunsDelta,
    long avgDurationMs,
    @Nullable Long avgDurationMsDelta,
    double incidentRate,
    @Nullable Double incidentRateDelta,
    long incidentCount,
    long activationCount,
    long avgTokensPerRun,
    long medianTokensPerRun,
    long p50DurationMs,
    long p95DurationMs) {}
