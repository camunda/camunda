/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * A signal into the forward-looking, per-instance no-incident metric, keyed to the START cohort of
 * the instance. Each instance emits an {@code activation = true} signal when it starts, and — only
 * the <em>first</em> time it raises an incident — an {@code activation = false} signal. Both carry
 * the instance's {@code startTime} as the event time so they fall in the same start window.
 *
 * <p>Because the incident signal fires when the incident is created (while the instance may still
 * be running) rather than at completion, an in-flight instance's incident lowers the cohort's
 * no-incident share immediately, and a stuck instance is never invisible. Firing only on the first
 * incident makes the count distinct per instance (multiple incidents collapse to one). {@code
 * version} is omitted from the key (the definition key implies it) so it isn't needed at incident
 * time, where the record does not carry it.
 */
public record IncidentCohortFact(
    String bpmnProcessId,
    long processDefinitionKey,
    String tenantId,
    long startTime,
    boolean activation,
    int sourcePartitionId,
    long sourcePosition)
    implements ProcessExecutionFact {}
