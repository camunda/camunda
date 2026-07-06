/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.store.AggregatedFetch;
import io.camunda.analytics.dataset.store.DatasetFetch;
import io.camunda.analytics.dataset.store.ReadStrategy;
import io.camunda.analytics.meter.PushdownSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides <b>how</b> to answer a {@link ReportQuery}: which tier to read per meter, what to fetch
 * from the backend, and what the executor rolls up in the application. Backend-neutral — it reasons
 * only about the {@link CompiledDataset}'s grain, meters, and available tiers, never about SQL or
 * an index.
 *
 * <p>Per meter it picks the <em>coarsest</em> tier whose window divides the requested granularity
 * (so a daily bucket is built from the 1d tier, not re-summed from 1m), falling back to the finest
 * tier when none divides evenly. It then picks a {@link ReadStrategy} per meter: an additive meter
 * (with a {@link PushdownSpec}) reads {@code DIRECT} when the read is 1:1 with cells (granularity
 * == the tier and group-by == the full grain), else {@code PUSH_DOWN}; a sketch/summary always
 * {@code STREAM_MERGE}s (its finalized value is not combinable). Meters sharing a {@code (tier,
 * strategy)} share one fetch: additive meters an {@link AggregatedFetch} the store reduces, sketch
 * meters a {@link DatasetFetch} the executor streams and app-merges.
 */
public final class DatasetQueryPlanner {

  public QueryPlan plan(final ReportQuery query, final CompiledDataset dataset) {
    validateGroupBy(query, dataset);

    final boolean fullGrain = query.groupBy().size() == dataset.grain().columns().size();

    // Sketch/blob meters grouped by tier (streamed); additive meters grouped by (tier, strategy).
    final Map<Long, List<String>> streamByTier = new LinkedHashMap<>();
    final Map<AggKey, List<String>> aggByKey = new LinkedHashMap<>();
    for (final String meter : query.meters()) {
      final long tier = chooseTier(meter, dataset, query.granularityMs());
      final Optional<PushdownSpec<?, ?>> spec = specOf(meter, dataset);
      if (spec.isEmpty()) {
        streamByTier.computeIfAbsent(tier, t -> new ArrayList<>()).add(meter);
        continue;
      }
      final ReadStrategy strategy =
          query.granularityMs() == tier && fullGrain ? ReadStrategy.DIRECT : ReadStrategy.PUSH_DOWN;
      aggByKey.computeIfAbsent(new AggKey(tier, strategy), k -> new ArrayList<>()).add(meter);
    }

    final List<DatasetFetch> streamFetches = new ArrayList<>();
    streamByTier.forEach(
        (windowSize, meters) ->
            streamFetches.add(
                new DatasetFetch(
                    dataset, windowSize, query.fromMs(), query.toMs(), query.filters(), meters)));

    final List<AggregatedFetch> aggregatedFetches = new ArrayList<>();
    aggByKey.forEach(
        (key, meters) ->
            aggregatedFetches.add(
                new AggregatedFetch(
                    dataset,
                    key.tier(),
                    query.fromMs(),
                    query.toMs(),
                    query.filters(),
                    meters,
                    query.groupBy(),
                    query.granularityMs(),
                    key.strategy())));

    return new QueryPlan(
        dataset,
        streamFetches,
        aggregatedFetches,
        query.groupBy(),
        query.granularityMs(),
        query.meters());
  }

  /** The pushdown spec of {@code meter} (tier-independent), or empty for a sketch/summary. */
  private static Optional<PushdownSpec<?, ?>> specOf(
      final String meter, final CompiledDataset dataset) {
    for (final CompiledMeter compiled : dataset.meters()) {
      if (compiled.meterName().equals(meter)) {
        return compiled.pushdown();
      }
    }
    throw new IllegalArgumentException(
        "dataset '" + dataset.name() + "' has no meter '" + meter + "'");
  }

  /** A meter grouping key: same tier and same strategy share one aggregated fetch. */
  private record AggKey(long tier, ReadStrategy strategy) {}

  private static long chooseTier(
      final String meter, final CompiledDataset dataset, final long granularityMs) {
    long coarsestDividing = -1L;
    long finest = Long.MAX_VALUE;
    boolean found = false;
    for (final CompiledMeter compiled : dataset.meters()) {
      if (!compiled.meterName().equals(meter)) {
        continue;
      }
      found = true;
      final long windowMs = compiled.windowMs();
      finest = Math.min(finest, windowMs);
      if (windowMs <= granularityMs && granularityMs % windowMs == 0) {
        coarsestDividing = Math.max(coarsestDividing, windowMs);
      }
    }
    if (!found) {
      throw new IllegalArgumentException(
          "dataset '" + dataset.name() + "' has no meter '" + meter + "'");
    }
    return coarsestDividing > 0 ? coarsestDividing : finest;
  }

  private static void validateGroupBy(final ReportQuery query, final CompiledDataset dataset) {
    for (final String dim : query.groupBy()) {
      if (dataset.grain().indexOf(dim) < 0) {
        throw new IllegalArgumentException(
            "group-by dimension '" + dim + "' is not in the grain of '" + dataset.name() + "'");
      }
    }
  }
}
