/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

/**
 * Flat, precomputed plan for folding raw rows into this entity's metrics — precomputed once at
 * {@link CompiledEntityMetrics} build time so a future flush-thread rider needs no per-record
 * interpretation of the declaration (name lookups, type dispatch) on its hot path. Not wired into
 * any pipeline in this milestone; see {@code io.camunda.analytics.lake.metrics}'s package javadoc.
 *
 * @param dimColumnIndexes raw-schema column index of each declared dim, in declared order
 * @param measureColumnIndexes raw-schema column index of each declared measure, in declared order
 * @param windowMicros the window duration in microseconds; {@code 0} means {@code NONE} (no window
 *     dimension — every record folds into one all-time group per dims)
 * @param windowSourceColumn raw-schema column index of the epoch-microseconds column each row's
 *     window slot derives from (the declared source, defaulting to the raw schema's familyDaySource
 *     column); {@code -1} when unwindowed
 * @param runPrefixLength number of leading dims (in declared order) that are also a prefix of the
 *     raw schema's own sort-key order — a rider can detect a run of consecutive rows sharing this
 *     prefix directly off the raw schema's sort order without a dictionary lookup; the remaining
 *     dims (if any) need dict-indexed (hash-table) accumulation instead
 */
public record RiderPlan(
    int[] dimColumnIndexes,
    int[] measureColumnIndexes,
    long windowMicros,
    int windowSourceColumn,
    int runPrefixLength) {}
