/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

/**
 * A cube's compiled periodic-snapshot configuration (the Kimball periodic-snapshot pattern): Stage
 * 2 folds the cube's <em>finalized</em> finest-window cells into a durable cumulative accumulator
 * per key and, whenever the fold crosses a boundary of the {@code everyMs} event-time grid, emits
 * the absolute values as a row of the cube's {@code _snapshots} table. {@code cellGroup} prefixes
 * the sampler's durable accumulator state in the shared cell store — stable and never reused, like
 * a tier's.
 *
 * <p>Snapshots are first-class in the declared model and Stage-2-local at runtime: they have no
 * stream identity, never travel the shuffle, and need no dedup — a sample is a deterministic
 * derivation over already-deduped, already-finalized cells (see ADR 0010).
 */
public record CompiledSnapshots(long everyMs, int cellGroup) {}
