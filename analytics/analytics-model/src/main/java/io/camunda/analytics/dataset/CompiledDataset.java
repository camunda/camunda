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
import java.util.List;

/**
 * A {@link DatasetDeclaration} resolved into everything the pipeline needs to run a cube: the
 * {@link FactBinding} (source fact, filters, enrichment), the {@link DimensionSchema grain}, the
 * {@link CompiledMeter}s (one per meter per tier, each with a stable aggId), and the physical
 * {@link DatasetSchema}. Produced by {@link DatasetCompiler}; consumed by Stage 1 (seal per meter)
 * and Stage 2 (merge + write). A compiled dataset is shared via the catalog across partition tasks
 * and threads, so it carries no mutable state — each aggregation constructs its own single-writer
 * {@link DimensionKeySelector} over the grain.
 */
public record CompiledDataset(
    long cubeId,
    String name,
    FactBinding factBinding,
    DimensionSchema grain,
    List<CompiledMeter> meters,
    DatasetSchema schema) {

  public CompiledDataset {
    meters = List.copyOf(meters);
  }
}
