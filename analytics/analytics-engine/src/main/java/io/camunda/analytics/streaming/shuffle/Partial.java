/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.shuffle;

/**
 * A pre-aggregated windowed partial — the unit shuffled from Stage 1 (combiner) to Stage 2
 * (reducer) over the facts topic. It is <em>not</em> a per-instance fact: it is one source
 * partition's folded accumulator for a {@code (aggId, key, window)} cell, so the shuffle carries
 * far less than the raw stream and the per-source-partition parallelism is preserved.
 *
 * <ul>
 *   <li>{@code aggId} — which aggregation (metric/tier) this partial feeds; Stage 2 dispatches on
 *       it.
 *   <li>{@code key} — the encoded grouping key; the partial is routed to a facts partition by
 *       {@code hash(aggId, key)} so every writer's partial for a cell lands on one Stage-2
 *       consumer.
 *   <li>{@code windowStart} — the event-time window.
 *   <li>{@code writer} — the source partition that produced this partial (the stable writer
 *       identity for Stage 2's per-writer slots).
 *   <li>{@code acc} — the encoded accumulator (this writer's current full value for the cell).
 * </ul>
 */
public record Partial(int aggId, byte[] key, long windowStart, int writer, byte[] acc) {}
