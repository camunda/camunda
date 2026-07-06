/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The read-facing result of the composite {@link ExecutionTimeSummary}: count, average, exact
 * min/max, and the estimated duration at each requested percentile rank (parallel arrays, {@code
 * quantilesMs[i]} for {@code ranks[i]}). Count/min/max are exact; averages and percentiles are
 * derived (percentiles approximate).
 *
 * @param count the number of observations
 * @param averageMs the mean duration ({@code 0} when empty)
 * @param minMs the smallest observed duration ({@code 0} when empty)
 * @param maxMs the largest observed duration ({@code 0} when empty)
 * @param ranks the requested percentile ranks, fractions in [0, 1]
 * @param quantilesMs the estimated duration at each rank, parallel to {@code ranks}
 * @param durationBands the estimated observation count in each fixed duration band ({@code [≤10s,
 *     ≤30s, ≤60s, ≤120s, &gt;120s]}, derived from the sketch CDF); the bands sum to {@code count}
 *     and feed the completion-time histogram
 */
public record ExecutionTimeSummaryResult(
    long count,
    double averageMs,
    long minMs,
    long maxMs,
    double[] ranks,
    double[] quantilesMs,
    long[] durationBands) {}
