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
 * Runs a {@link ReportQuery} end to end: {@link DatasetQueryPlanner} decides the plan, the {@link
 * DatasetQueryClient} fetches raw {@link Cell}s per tier, and this executor performs the
 * application reduction — projecting each cell to the requested group-by dimensions, bucketing its
 * window at the requested granularity, and merging each meter's accumulators via the meter's own
 * {@code AggregateFunction.merge} before finalizing with {@code getResult}. This uniform app-merge
 * is exact for every meter class (additive counters and mergeable sketches alike), which is why the
 * backend need only filter and fetch.
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

    // (group-by values + time bucket) -> meter name -> running accumulator (Object; typed per
    // meter)
    final Map<GroupKey, Map<String, Object>> accumulators = new LinkedHashMap<>();

    for (final DatasetFetch fetch : plan.fetches()) {
      for (final Cell cell : client.fetch(fetch)) {
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
              meter, decode(bound, bytes), (current, decoded) -> merge(bound, current, decoded));
        }
      }
    }

    final List<ReportRow> rows = new ArrayList<>(accumulators.size());
    accumulators.forEach(
        (group, byMeter) -> {
          final Map<String, Object> measures = new LinkedHashMap<>();
          byMeter.forEach(
              (meter, acc) ->
                  measures.put(meter, result(boundOf(dataset, meter, tierOf(plan, meter)), acc)));
          rows.add(new ReportRow(group.dimensionMap(plan.groupBy()), group.bucket(), measures));
        });
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

  private static long tierOf(final QueryPlan plan, final String meter) {
    for (final DatasetFetch fetch : plan.fetches()) {
      if (fetch.meters().contains(meter)) {
        return fetch.windowSize();
      }
    }
    throw new IllegalStateException("meter '" + meter + "' absent from the plan");
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
