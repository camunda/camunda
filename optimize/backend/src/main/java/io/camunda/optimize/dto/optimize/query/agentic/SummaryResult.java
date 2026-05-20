/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.agentic;

import org.jspecify.annotations.NullMarked;

/**
 * Raw repository result for the summary query. Service calls the repository twice (current +
 * previous period) and computes delta fields before mapping to SummaryResponse.
 */
@NullMarked
public record SummaryResult(
    long totalRuns,
    long avgDurationMs,
    long p50DurationMs,
    long p95DurationMs,
    long totalInputTokens,
    long totalOutputTokens,
    long medianTokensPerRun,
    long incidentCount) {}
