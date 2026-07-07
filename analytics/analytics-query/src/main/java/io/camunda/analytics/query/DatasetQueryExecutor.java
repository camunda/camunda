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
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.serving.spi.AggregatedFetch;
import io.camunda.analytics.serving.spi.AggregatedRow;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.RecordValue;
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
 * group-by, bucketing its window at the requested granularity, merging decoded deltas into one
 * running accumulator per (group, meter) via {@code AggregateFunction.mergeInto}, then finalizing
 * with {@code getResult}. The app-merge is exact for every mergeable sketch, and the pushdown is
 * exact for every additive meter, so a mixed cube is one union of the two.
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
    // Resolve each meter's bound meter and one codec flyweight once per fetch, not per cell: the
    // bound lookup is a linear scan of the dataset's meters and the codec is a fresh flyweight per
    // call, and the app-merge here is single-threaded, so one reused instance per meter is safe.
    final Map<String, ResolvedMeter> resolvedByMeter = new LinkedHashMap<>();
    final Map<GroupKey, Map<String, Object>> accumulators = new LinkedHashMap<>();
    for (final DatasetFetch fetch : plan.streamFetches()) {
      final Map<String, ResolvedMeter> resolved = new LinkedHashMap<>();
      for (final String meter : fetch.meters()) {
        final ResolvedMeter resolvedMeter =
            ResolvedMeter.of(boundOf(dataset, meter, fetch.windowSize()));
        resolved.put(meter, resolvedMeter);
        resolvedByMeter.putIfAbsent(meter, resolvedMeter);
      }
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
              resolved.get(meter).mergeDelta(byMeter, meter, bytes);
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
                  measures.put(meter, resolvedByMeter.get(meter).aggregate().getResult(acc)));
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

  private static long alignDown(final long value, final long bucket) {
    return value - Math.floorMod(value, bucket);
  }

  /**
   * One streamed meter's per-query merge context: the aggregate function plus a single reusable
   * codec flyweight, resolved once instead of per cell. Deltas are decoded through the codec's
   * merge-only path (possibly a read-only view over the cell's bytes) and folded in place into a
   * running accumulator this executor owns — the first delta seeds a fresh accumulator, so a
   * decoded view is never stored.
   */
  private record ResolvedMeter(
      AggregateFunction<Object, Object, Object> aggregate, RecordValue<Object> codec) {

    @SuppressWarnings("unchecked")
    static ResolvedMeter of(final BoundMeter<?, ?> bound) {
      final BoundMeter<Object, Object> cast = (BoundMeter<Object, Object>) bound;
      return new ResolvedMeter(
          (AggregateFunction<Object, Object, Object>) (AggregateFunction<?, ?, ?>) cast.aggregate(),
          cast.accumulatorCodec());
    }

    void mergeDelta(final Map<String, Object> byMeter, final String meter, final byte[] bytes) {
      final Object delta = codec.fromBytesForMerge(bytes);
      final Object current = byMeter.get(meter);
      byMeter.put(
          meter,
          current == null
              ? aggregate.mergeInto(aggregate.createAccumulator(), delta)
              : aggregate.mergeInto(current, delta));
    }
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
