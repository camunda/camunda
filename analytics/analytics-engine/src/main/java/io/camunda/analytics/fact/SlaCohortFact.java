/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * A signal into the <em>forward-looking</em> SLA-met metric, keyed to the cohort of instances that
 * <em>started</em> in a window (not the ones that completed there). Each instance emits two of
 * these, both carrying its {@code startTime} as the event time so they land in the same start
 * cohort:
 *
 * <ul>
 *   <li>{@code activation = true} on process-instance activation — the "started" signal (the
 *       cohort's denominator);
 *   <li>{@code activation = false} on completion/termination — the "outcome" signal, carrying the
 *       final {@code durationMs} and whether it {@code completedNormally}, from which the SLA
 *       aggregate decides whether it met the target (the numerator).
 * </ul>
 *
 * <p>Because the outcome signal is stamped with the instance's <em>start</em> time, it arrives into
 * an already-past window; the SLA rollup therefore keeps each start cohort open for the length of
 * the SLA target (allowed lateness), which is exactly the point at which the cohort matures — after
 * that, any started instance not yet counted as met is a breach (it either finished late or is
 * still running past its deadline). {@code sourcePartitionId}/{@code sourcePosition} dedup replays.
 */
public record SlaCohortFact(
    String bpmnProcessId,
    long processDefinitionKey,
    int version,
    String tenantId,
    long startTime,
    boolean activation,
    boolean completedNormally,
    long durationMs,
    int sourcePartitionId,
    long sourcePosition)
    implements ProcessExecutionFact {}
