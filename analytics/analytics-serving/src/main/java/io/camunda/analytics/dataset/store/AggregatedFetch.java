/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * The backend-neutral <b>pushed-down</b> fetch spec: like a {@link DatasetFetch}, but the store
 * does the reduction and returns finalized {@link AggregatedRow}s rather than raw {@link Cell}s.
 * Used for the {@link ReadStrategy#PUSH_DOWN} and {@link ReadStrategy#DIRECT} strategies (never
 * {@link ReadStrategy#STREAM_MERGE}, which streams cells).
 *
 * <p>Reads {@code meters} of {@code dataset} at one tier ({@code windowSize}) whose window falls in
 * {@code [fromMs, toMs)} and that satisfy {@code filters}, grouped by {@code groupBy} and bucketed
 * at {@code granularityMs} (the derived bucket is {@code window_start − (window_start mod
 * granularity)}). For {@code DIRECT} the granularity equals the tier and {@code groupBy} is the
 * full grain, so the reduction is a no-op (one row per cell).
 */
public record AggregatedFetch(
    CompiledDataset dataset,
    long windowSize,
    long fromMs,
    long toMs,
    List<FilterPredicate> filters,
    List<String> meters,
    List<String> groupBy,
    long granularityMs,
    ReadStrategy strategy) {

  public AggregatedFetch {
    filters = List.copyOf(filters);
    meters = List.copyOf(meters);
    groupBy = List.copyOf(groupBy);
    if (strategy == ReadStrategy.STREAM_MERGE) {
      throw new IllegalArgumentException("STREAM_MERGE does not use an AggregatedFetch");
    }
  }
}
