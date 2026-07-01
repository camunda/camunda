/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window's duration distribution for a process (or element): the observation count, exact
 * min/max, and the p50/p75/p90/p99 estimates the pipeline derived from the KLL sketch. A series of
 * these ordered by {@code windowStart} is the control-chart trend; the latest point feeds the
 * number tiles.
 */
public record DurationPercentilePoint(
    long windowStart,
    long observationCount,
    long minMs,
    long maxMs,
    long p50Ms,
    long p75Ms,
    long p90Ms,
    long p99Ms) {}
