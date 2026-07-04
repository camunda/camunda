/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

/**
 * The fact derived by Stage A when a process instance reaches a terminal state (COMPLETED /
 * TERMINATED) or its SLA timer fires.
 *
 * <p>This is the boundary object between the projection and the aggregation. It is intentionally
 * small and flat: everything Stage B/C needs to aggregate, plus the source coordinates used by the
 * forward-only gate.
 *
 * @param processId the grouping key for the downstream aggregate
 * @param tenantId carried for completeness / multi-tenant partitioning (unused by the aggregate here)
 * @param startWindowMs {@code startMs} floored to the minute — the event-time window this fact belongs to
 * @param durationMs {@code endMs - startMs}; for an SLA breach this is the elapsed time at breach
 * @param hadIncident whether an INCIDENT event was seen during the instance's life
 * @param slaBreached true when this fact was emitted by the SLA punctuator rather than a real completion
 * @param sourcePartition physical source partition of the terminal event
 * @param sourceOffset physical source offset of the terminal event (drives the forward-only gate)
 */
public record CompletionFact(
    String processId,
    String tenantId,
    long startWindowMs,
    long durationMs,
    boolean hadIncident,
    boolean slaBreached,
    int sourcePartition,
    long sourceOffset) {}
