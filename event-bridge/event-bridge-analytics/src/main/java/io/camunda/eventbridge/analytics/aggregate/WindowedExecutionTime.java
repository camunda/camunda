/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

/**
 * One windowed cell of the aggregated dataset: process-instance execution time rolled up per
 * process definition (key + version), tenant, and a fixed event-time window {@code [windowStart,
 * windowStart + windowSizeMs)}, bucketed by completion event-time. The headline Phase-1 metric — "N
 * instances completed for a definition in the last hour" — is {@link #completedCount()} for the
 * relevant window(s).
 *
 * <p>{@code finalized} reflects whether the event-time watermark has passed this window's end (plus
 * allowed lateness): once final the count no longer changes; before that a straggler completion may
 * still increment it.
 */
public record WindowedExecutionTime(
    long processDefinitionKey,
    String bpmnProcessId,
    int version,
    String tenantId,
    long windowStart,
    long windowSizeMs,
    long completedCount,
    long totalDurationMs,
    long minDurationMs,
    long maxDurationMs,
    boolean finalized) {

  public long windowEnd() {
    return windowStart + windowSizeMs;
  }

  public double averageDurationMs() {
    return completedCount == 0 ? 0.0 : (double) totalDurationMs / completedCount;
  }
}
