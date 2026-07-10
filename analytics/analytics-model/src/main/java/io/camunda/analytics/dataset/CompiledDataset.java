/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.meter.BoundMeter;
import java.util.List;

/**
 * A {@link DatasetDeclaration} resolved into everything the pipeline needs to run a cube: the
 * {@link FactBinding} (source fact, filters, enrichment), the {@link DimensionSchema grain}, the
 * cube's shuffle {@code streamId}, its {@link CompiledMeter meters} (the composite accumulator's
 * slots, in declaration order), its {@link CompiledTier tiers} (finest first), and the physical
 * {@link DatasetSchema}. Produced by {@link DatasetCompiler}; consumed by Stage 1 (one composite
 * fold + seal per cube) and Stage 2 (per-tier composite merge + one-row write) — see ADR 0009:
 * {@code (streamId, dimensionKey)} is the single identity threaded through gate, fold, route,
 * dedup, merge, and serving row. A compiled dataset is shared via the catalog across partition
 * tasks and threads, so it carries no mutable state — each aggregation constructs its own
 * single-writer {@link DimensionKeySelector} over the grain.
 */
public record CompiledDataset(
    long cubeId,
    String name,
    FactBinding factBinding,
    DimensionSchema grain,
    int streamId,
    List<CompiledMeter> meters,
    List<CompiledTier> tiers,
    CompiledSnapshots snapshots,
    DatasetSchema schema) {

  public CompiledDataset {
    meters = List.copyOf(meters);
    tiers = List.copyOf(tiers);
    if (tiers.isEmpty()) {
      throw new IllegalArgumentException("cube '" + name + "' must declare at least one tier");
    }
    for (int i = 1; i < tiers.size(); i++) {
      if (tiers.get(i).windowMs() <= tiers.get(i - 1).windowMs()) {
        throw new IllegalArgumentException(
            "cube '" + name + "' tiers must be strictly increasing, finest first");
      }
    }
  }

  /** The finest tier — the only one Stage 1 aggregates and ships; Stage 2 derives the rest. */
  public CompiledTier finestTier() {
    return tiers.get(0);
  }

  /** Whether this cube materialises periodic snapshots (see {@link CompiledSnapshots}). */
  public boolean hasSnapshots() {
    return snapshots != null;
  }

  /** The meters' bound aggregates/codecs in slot order — the composite's construction input. */
  public List<BoundMeter<?, ?>> meterBounds() {
    return meters.stream().map(CompiledMeter::bound).toList();
  }
}
