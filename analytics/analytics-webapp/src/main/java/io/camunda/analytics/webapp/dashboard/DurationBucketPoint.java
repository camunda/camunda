/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * Completion-time distribution for one start cohort: of {@code started} instances, how many
 * finished in each duration band ({@code bands} = [&lt;10s, &lt;30s, &lt;60s, &lt;120s, ≥120s],
 * exact counts from the {@code duration_bands} histogram meter); {@code open} are still running
 * (started − Σ bands).
 */
public record DurationBucketPoint(long windowStart, long started, long[] bands, long open) {}
