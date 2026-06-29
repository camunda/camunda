/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

/**
 * An immutable fact: the execution time of a single process instance, derived once when the
 * instance completes (or terminates).
 *
 * <p>Facts are a deterministic function of the consumed record stream — they are recomputed by
 * replay rather than stored to avoid loss. {@code sourcePartitionId}/{@code sourcePosition} are the
 * coordinates of the completion record that derived this fact; downstream aggregation uses them as
 * an idempotency key to dedup the bounded duplicates that at-least-once delivery can produce across
 * a leader failover.
 */
public record ProcessInstanceExecutionTimeFact(
    long processInstanceKey,
    long processDefinitionKey,
    String bpmnProcessId,
    int version,
    String tenantId,
    long startTime,
    long endTime,
    long durationMs,
    boolean completedNormally,
    int sourcePartitionId,
    long sourcePosition) {}
