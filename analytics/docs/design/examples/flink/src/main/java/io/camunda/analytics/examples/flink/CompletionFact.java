/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.examples.flink;

import java.io.Serializable;

/**
 * A <em>derived</em> fact emitted by Stage A when an instance reaches a terminal state (completed
 * or terminated) or breaches its SLA. This is the record that flows over the Stage-A → Stage-B
 * shuffle and feeds the aggregate.
 *
 * <p>In our own design this is exactly the "fact stream" produced by the base projection: the base
 * projection consumes raw events and produces facts, one per instance completion, carrying the
 * source coordinate ({@code sourcePartition}, {@code sourceOffset}) so downstream can dedup and
 * gate on it.
 *
 * @param processId grouping key for Stage B/C
 * @param tenantId owning tenant (carried for completeness; not aggregated here)
 * @param startWindowMs {@link #startMs}-floored-to-1-minute; the coarse bucket the instance started
 *     in (kept on the fact for parity with the KS/Spark twins; the Flink window itself is derived
 *     independently from event time)
 * @param durationMs wall-clock duration in millis (endMs - startMs), or the elapsed SLA at breach
 * @param hadIncident whether an incident was ever raised on the instance
 * @param slaBreach {@code true} if this fact was emitted by the SLA timer rather than a natural
 *     completion; Stage B filters these out (the dataset is "completed instances")
 * @param sourcePartition source-log partition of the terminal event
 * @param sourceOffset source-log offset of the terminal event — the forward-only gate compares
 *     against this
 */
public record CompletionFact(
    String processId,
    String tenantId,
    long startWindowMs,
    long durationMs,
    boolean hadIncident,
    boolean slaBreach,
    int sourcePartition,
    long sourceOffset)
    implements Serializable {}
