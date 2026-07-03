/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides <b>how</b> to answer a {@link ReportQuery}: which tier to read per meter, what to fetch
 * from the backend, and what the executor rolls up in the application. Backend-neutral — it reasons
 * only about the {@link CompiledDataset}'s grain, meters, and available tiers, never about SQL or
 * an index.
 *
 * <p>Per meter it picks the <em>coarsest</em> tier whose window divides the requested granularity
 * (so a daily bucket is built from the 1d tier, not re-summed from 1m), falling back to the finest
 * tier when none divides evenly. Meters that land on the same tier share one {@link DatasetFetch},
 * since a tier is one set of stored rows. This iteration fetches at grain and leaves the
 * cross-window/cross-dimension combine to the executor (uniform app-merge); additive pushdown is a
 * later optimization that only changes the fetch, not this contract.
 */
public final class DatasetQueryPlanner {

  public QueryPlan plan(final ReportQuery query, final CompiledDataset dataset) {
    validateGroupBy(query, dataset);

    // Choose a tier per requested meter, then group meters by the chosen tier.
    final Map<Long, List<String>> metersByTier = new LinkedHashMap<>();
    for (final String meter : query.meters()) {
      final long tier = chooseTier(meter, dataset, query.granularityMs());
      metersByTier.computeIfAbsent(tier, t -> new ArrayList<>()).add(meter);
    }

    final List<DatasetFetch> fetches = new ArrayList<>();
    metersByTier.forEach(
        (windowSize, meters) ->
            fetches.add(
                new DatasetFetch(
                    dataset, windowSize, query.fromMs(), query.toMs(), query.filters(), meters)));

    return new QueryPlan(dataset, fetches, query.groupBy(), query.granularityMs(), query.meters());
  }

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
