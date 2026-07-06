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
import io.camunda.analytics.meter.BoundMeter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a {@link ReportQuery} end to end: {@link DatasetQueryPlanner} decides the per-meter
 * strategy, and this executor unions the results on {@code (group-by values, time bucket)}.
 * Additive meters come back finalized from the store ({@link DatasetQueryClient#fetchAggregated} —
 * {@code DIRECT} or {@code PUSH_DOWN}); sketch/summary meters are streamed ({@link
 * DatasetQueryClient#streamCells}) and app-merged here — projecting each cell to the requested
 * group-by, bucketing its window at the requested granularity, merging accumulators via {@code
 * AggregateFunction.merge}, then finalizing with {@code getResult}. The app-merge is exact for
 * every mergeable sketch, and the pushdown is exact for every additive meter, so a mixed cube is
 * one union of the two.
 */
public final class DatasetQueryExecutor {

  private final DatasetQueryPlanner planner;
  private final DatasetQueryClient client;

  public DatasetQueryExecutor(final DatasetQueryPlanner planner, final DatasetQueryClient client) {
    this.planner = planner;
    this.client = client;
  }

  public ReportResult execute(final ReportQuery query, final CompiledDataset dataset) {
    final QueryPlan plan = planner.plan(query, dataset);

    // (group-by values + time bucket) -> meter name -> finalized measure.
    final Map<GroupKey, Map<String, Object>> measuresByGroup = new LinkedHashMap<>();

    // Pushed-down / direct additive meters: the store already reduced and finalized them.
    for (final AggregatedFetch fetch : plan.aggregatedFetches()) {
      for (final AggregatedRow row : client.fetchAggregated(fetch)) {
        measuresByGroup
            .computeIfAbsent(
                new GroupKey(row.groupValues(), row.bucket()), g -> new LinkedHashMap<>())
            .putAll(row.measures());
      }
    }

    // Sketch/blob meters: stream cells and app-merge each meter's accumulator per (group, bucket).
    final Map<GroupKey, Map<String, Object>> accumulators = new LinkedHashMap<>();
    for (final DatasetFetch fetch : plan.streamFetches()) {
      client.streamCells(
          fetch,
          cell -> {
            final long bucket = alignDown(cell.windowStart(), plan.granularityMs());
            final GroupKey group = groupKeyOf(plan.groupBy(), cell, bucket);
            final Map<String, Object> byMeter =
                accumulators.computeIfAbsent(group, g -> new LinkedHashMap<>());
            for (final String meter : fetch.meters()) {
              final byte[] bytes = cell.accumulators().get(meter);
              if (bytes == null) {
                continue;
              }
              final BoundMeter<?, ?> bound = boundOf(dataset, meter, fetch.windowSize());
              byMeter.merge(
                  meter,
                  decode(bound, bytes),
                  (current, decoded) -> merge(bound, current, decoded));
            }
          });
    }
    // Finalize the streamed accumulators and fold them into the same group map.
    accumulators.forEach(
        (group, byMeter) -> {
          final Map<String, Object> measures =
              measuresByGroup.computeIfAbsent(group, g -> new LinkedHashMap<>());
          byMeter.forEach(
              (meter, acc) ->
                  measures.put(
                      meter, result(boundOf(dataset, meter, streamTierOf(plan, meter)), acc)));
        });

    final List<ReportRow> rows = new ArrayList<>(measuresByGroup.size());
    measuresByGroup.forEach(
        (group, measures) ->
            rows.add(new ReportRow(group.dimensionMap(plan.groupBy()), group.bucket(), measures)));
    return new ReportResult(rows);
  }

  private static GroupKey groupKeyOf(
      final List<String> groupBy, final Cell cell, final long bucket) {
    final List<Object> values = new ArrayList<>(groupBy.size());
    for (final String dim : groupBy) {
      values.add(cell.key().get(dim));
    }
    return new GroupKey(values, bucket);
  }

  private static BoundMeter<?, ?> boundOf(
      final CompiledDataset dataset, final String meter, final long windowSize) {
    for (final CompiledMeter compiled : dataset.meters()) {
      if (compiled.meterName().equals(meter) && compiled.windowMs() == windowSize) {
        return compiled.bound();
      }
    }
    throw new IllegalStateException(
        "no compiled meter '" + meter + "' at tier " + windowSize + " in '" + dataset.name() + "'");
  }

  private static long streamTierOf(final QueryPlan plan, final String meter) {
    for (final DatasetFetch fetch : plan.streamFetches()) {
      if (fetch.meters().contains(meter)) {
        return fetch.windowSize();
      }
    }
    throw new IllegalStateException("streamed meter '" + meter + "' absent from the plan");
  }

  private static long alignDown(final long value, final long bucket) {
    return value - Math.floorMod(value, bucket);
  }

  @SuppressWarnings("unchecked")
  private static Object decode(final BoundMeter<?, ?> bound, final byte[] bytes) {
    return ((BoundMeter<Object, Object>) bound).accumulatorCodec().fromBytes(bytes);
  }

  @SuppressWarnings("unchecked")
  private static Object merge(
      final BoundMeter<?, ?> bound, final Object current, final Object decoded) {
    return ((BoundMeter<Object, Object>) bound).aggregate().merge(current, decoded);
  }

  @SuppressWarnings("unchecked")
  private static Object result(final BoundMeter<?, ?> bound, final Object acc) {
    return ((BoundMeter<Object, Object>) bound).aggregate().getResult(acc);
  }

  /** A value-equal reduction key: the group-by dimension values (in order) plus the time bucket. */
  private record GroupKey(List<Object> dimensions, long bucket) {

    Map<String, Object> dimensionMap(final List<String> groupBy) {
      final Map<String, Object> map = new LinkedHashMap<>();
      for (int i = 0; i < groupBy.size(); i++) {
        map.put(groupBy.get(i), dimensions.get(i));
      }
      return map;
    }
  }
}
