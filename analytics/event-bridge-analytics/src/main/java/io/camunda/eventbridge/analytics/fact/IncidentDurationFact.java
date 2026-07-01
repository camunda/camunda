/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

/**
 * A resolved incident's open→resolve duration, attributed to the flow node that raised it. Emitted
 * when an {@code INCIDENT RESOLVED} is paired with its earlier {@code CREATED} (by element-instance
 * key); {@code resolvedTimeMs} is the event time. Aggregated per flow node it gives the average
 * incident-resolution time — Optimize's incident-duration heatmap.
 */
public record IncidentDurationFact(
    String bpmnProcessId,
    String elementId,
    String tenantId,
    long durationMs,
    long resolvedTimeMs,
    int sourcePartitionId,
    long sourcePosition)
    implements ProcessExecutionFact {}
