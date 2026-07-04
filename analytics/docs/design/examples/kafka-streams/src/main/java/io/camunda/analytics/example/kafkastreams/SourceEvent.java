/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

/**
 * The raw input record that arrives on the source topic.
 *
 * <p>This is the exact same shape used by the Flink and Spark reference examples so the three can
 * be compared side by side. It models the handful of process-engine events we care about for
 * instance-duration analytics.
 *
 * <p>{@code sourcePartition} and {@code sourceOffset} are the physical coordinates of this record in
 * the source log. We carry them all the way through so the downstream aggregate can implement a
 * deterministic, replay-safe "forward-only" gate (see {@code AnalyticsTopology}). This is the same
 * coordinate a hand-rolled runtime would use for segment-origin dedup — we keep it here to make the
 * comparison concrete.
 */
public record SourceEvent(
    EventType type,
    long instanceKey,
    String processId,
    String tenantId,
    long timestampMs,
    String elementId,
    String varName,
    String varValue,
    int sourcePartition,
    long sourceOffset) {

  public enum EventType {
    ACTIVATED,
    COMPLETED,
    TERMINATED,
    VARIABLE,
    INCIDENT
  }
}
