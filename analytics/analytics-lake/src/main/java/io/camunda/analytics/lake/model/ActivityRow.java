/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.model;

/**
 * One finished element instance — the "activity table" row, written exactly once when the element
 * completes or terminates. Carries its own denormalized filter columns (tenant, process id,
 * version) so element-level queries never join; carries <b>no</b> variables by design.
 *
 * @param instanceKey the owning process instance key
 * @param processId the BPMN process id (denormalized)
 * @param version the definition version (denormalized)
 * @param tenantId the tenant (denormalized)
 * @param elementId the BPMN element id
 * @param elementType the BPMN element type name (e.g. {@code SERVICE_TASK})
 * @param elementKey the element instance key
 * @param state {@code COMPLETED} or {@code TERMINATED}
 * @param startMs epoch millis of element activation
 * @param endMs epoch millis of element completion/termination
 * @param durationMs {@code endMs - startMs}
 * @param instanceStartMs the owning instance's start (the family date for partition placement and
 *     retention)
 */
public record ActivityRow(
    long instanceKey,
    String processId,
    int version,
    String tenantId,
    String elementId,
    String elementType,
    long elementKey,
    String state,
    long startMs,
    long endMs,
    long durationMs,
    long instanceStartMs) {}
