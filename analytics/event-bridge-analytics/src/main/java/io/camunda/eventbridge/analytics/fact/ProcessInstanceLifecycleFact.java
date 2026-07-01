/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.fact;

/**
 * A signed change to the count of in-flight process instances: {@code +1} when a process instance
 * activates, {@code -1} when it completes or terminates. Summed (in a single all-time bucket) per
 * definition, the running total is the current number of <em>active</em> instances — a gauge, not a
 * windowed measure. {@code sourcePartitionId}/{@code sourcePosition} dedup replays like the other
 * facts.
 */
public record ProcessInstanceLifecycleFact(
    String bpmnProcessId,
    long processDefinitionKey,
    int version,
    String tenantId,
    long delta,
    long timestamp,
    int sourcePartitionId,
    long sourcePosition)
    implements ProcessExecutionFact {}
