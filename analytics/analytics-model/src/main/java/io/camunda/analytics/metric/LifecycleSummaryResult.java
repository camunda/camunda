/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

/**
 * The read-facing result of a {@link LifecycleSummary}: the per-transition counts plus the duration
 * statistics of the ended events.
 */
public record LifecycleSummaryResult(
    long activated, long completed, long terminated, ExecutionTimeSummaryResult duration) {}
