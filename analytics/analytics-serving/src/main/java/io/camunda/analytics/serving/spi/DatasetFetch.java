/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * The backend-neutral <b>fetch spec</b> the {@link DatasetQueryPlanner} produces and the {@link
 * DatasetQueryClient} executes: fetch the cells of {@code dataset} at one tier ({@code windowSize})
 * whose window falls in {@code [fromMs, toMs)} and that satisfy {@code filters}, returning the
 * requested {@code meters}' accumulators. Grain-level (no group-by pushdown this iteration) — the
 * executor rolls up dropped dimensions and buckets windows in the application.
 *
 * <p>One fetch is emitted per distinct tier among the requested meters, since meters at different
 * tiers live in different stored rows.
 */
public record DatasetFetch(
    CompiledDataset dataset,
    long windowSize,
    long fromMs,
    long toMs,
    List<FilterPredicate> filters,
    List<String> meters) {

  public DatasetFetch {
    filters = List.copyOf(filters);
    meters = List.copyOf(meters);
  }
}
