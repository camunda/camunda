/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.aggregate;

/**
 * One row of the aggregated dataset: process-instance execution time rolled up per process
 * definition (key + version) and tenant. {@code averageDurationMs()} is derived at read time so the
 * stored aggregate stays additive (count + sum + min + max), which is what keeps the fold a simple
 * incremental upsert.
 */
public record ExecutionTimeAggregate(
    long processDefinitionKey,
    String bpmnProcessId,
    int version,
    String tenantId,
    long instanceCount,
    long totalDurationMs,
    long minDurationMs,
    long maxDurationMs) {

  public double averageDurationMs() {
    return instanceCount == 0 ? 0.0 : (double) totalDurationMs / instanceCount;
  }
}
