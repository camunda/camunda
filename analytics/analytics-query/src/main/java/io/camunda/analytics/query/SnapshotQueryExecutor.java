/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.SnapshotPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers SNAPSHOT queries — "where did the value stand at each moment?" (ADR 0010) — as opposed to
 * the aggregate executor's flow questions ("how much happened per bucket?"). The plan is
 * deliberately simple: one BASELINE fetch (per key, the newest snapshot at-or-before the range
 * start — the opening balance) plus one RANGE fetch (the sparse change points), then a
 * carry-forward walk that materialises a dense series per key: each bucket's value is the newest
 * snapshot at-or-before it. Cost is O(baseline keys + points in range + buckets×keys) — never
 * O(history), which is what makes snapshot retention/thinning safe for reads.
 *
 * <p>A key with no baseline and no in-range point does not appear; a key with a baseline but no
 * in-range points yields a flat line — the case that proves the baseline fetch must exist. Values
 * are the meters' recomposed read results; series are per full grain key (cross-key roll-up of
 * these semi-additive values is a follow-up).
 */
public final class SnapshotQueryExecutor {

  private final DatasetQueryClient client;

  public SnapshotQueryExecutor(final DatasetQueryClient client) {
    this.client = client;
  }

  /** A snapshot (as-of) query: dense per-key series over {@code (fromMs, toMs]} at granularity. */
  public record SnapshotQuery(long fromMs, long toMs, long granularityMs) {}

  /** One point of the materialised series: the key, the bucket end, the absolute values. */
  public record SnapshotSeriesPoint(
      List<Object> keyValues, long time, Map<String, Object> measures) {}

  public List<SnapshotSeriesPoint> execute(
      final SnapshotQuery query, final CompiledDataset dataset) {
    if (!dataset.hasSnapshots()) {
      throw new IllegalArgumentException(
          "dataset '" + dataset.name() + "' declares no snapshots — a SNAPSHOT query needs them");
    }
    final long everyMs = dataset.snapshots().everyMs();
    if (query.granularityMs() <= 0 || query.granularityMs() % everyMs != 0) {
      throw new IllegalArgumentException(
          "snapshot granularity "
              + query.granularityMs()
              + " ms must be a positive multiple of the cube's sample interval ("
              + everyMs
              + " ms)");
    }
    if (query.toMs() <= query.fromMs()) {
      throw new IllegalArgumentException("empty snapshot range");
    }

    // The opening balance per key, then the sparse change points — both ordered by key.
    final Map<List<Object>, List<SnapshotPoint>> byKey = new LinkedHashMap<>();
    for (final SnapshotPoint point : client.snapshotBaseline(dataset, query.fromMs())) {
      byKey.computeIfAbsent(point.keyValues(), key -> new ArrayList<>()).add(point);
    }
    for (final SnapshotPoint point : client.snapshotRange(dataset, query.fromMs(), query.toMs())) {
      byKey.computeIfAbsent(point.keyValues(), key -> new ArrayList<>()).add(point);
    }

    final List<SnapshotSeriesPoint> series = new ArrayList<>();
    for (final Map.Entry<List<Object>, List<SnapshotPoint>> entry : byKey.entrySet()) {
      carryForward(entry.getKey(), entry.getValue(), query, series);
    }
    return series;
  }

  /**
   * Walks the buckets of {@code (fromMs, toMs]}, carrying the newest point at-or-before each bucket
   * end forward. Points arrive ordered (baseline first, then range ascending — the fetch
   * contracts); a key emits nothing before its first point ("didn't exist yet"), and a
   * baseline-only key emits its flat line.
   */
  private static void carryForward(
      final List<Object> keyValues,
      final List<SnapshotPoint> points,
      final SnapshotQuery query,
      final List<SnapshotSeriesPoint> series) {
    int next = 0;
    Map<String, Object> current = null;
    for (long bucket = query.fromMs() + query.granularityMs();
        bucket <= query.toMs();
        bucket += query.granularityMs()) {
      while (next < points.size() && points.get(next).sampleTime() <= bucket) {
        current = points.get(next).measures();
        next++;
      }
      if (current != null) {
        series.add(new SnapshotSeriesPoint(keyValues, bucket, current));
      }
    }
  }
}
