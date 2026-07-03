/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * A signed change to the incident count for a flow node: {@code +1} when an incident is created,
 * {@code -1} when it is resolved. Summed over a window it gives the number of incidents raised (the
 * created events); summed in a single all-time bucket it gives the number currently open (created
 * minus resolved). Because an instance can raise several incidents, these count <em>incidents</em>,
 * not instances. {@code sourcePartitionId}/{@code sourcePosition} dedup replays like the other
 * facts.
 */
public record IncidentFact(
    String bpmnProcessId,
    String elementId,
    String tenantId,
    String errorType,
    long delta,
    long timestamp,
    int sourcePartitionId,
    long sourcePosition)
    implements ProcessExecutionFact {}
