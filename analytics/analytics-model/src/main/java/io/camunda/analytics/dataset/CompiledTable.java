/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionColumn;
import java.util.List;

/**
 * A {@link DatasetKind#PROJECTED} declaration resolved into everything the pipeline needs to run a
 * raw dataset: the {@link FactBinding} (source fact, filters, enrichment), the {@code keyField}
 * (the fact field used as the row primary key), and the projected {@code columns}. Produced by
 * {@link DatasetCompiler#compileTable}; consumed by Stage 1, which upserts one row per matching
 * fact keyed by {@code keyField} — idempotent under replay, so no shuffle or reduce is needed.
 */
public record CompiledTable(
    long cubeId,
    String name,
    FactBinding factBinding,
    String keyField,
    List<DimensionColumn> columns) {

  public CompiledTable {
    columns = List.copyOf(columns);
  }
}
