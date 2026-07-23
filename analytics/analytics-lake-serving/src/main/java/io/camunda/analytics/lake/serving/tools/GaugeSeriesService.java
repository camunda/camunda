/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/gauge-series}: a time-bucketed read of {@code open_instances_gauge} (see
 * {@code analytics-lake}'s {@code OpenInstancesGaugeWriter}) -- a plain periodic sample table (no
 * windowed fold, no {@code _metrics}/{@code _hist} pair), so it has no {@link
 * io.camunda.analytics.lake.serving.catalog.MetricRegistry} entity behind it and reads the raw view
 * directly, the same way {@link ConditionsService} reads {@code variants} directly. A missing view
 * (a warehouse that predates this table) degrades to an empty point list, never an error -- the
 * WIP-over-time tile renders "the gauge starts with the next deploy" either way.
 *
 * <h2>Why {@code AVG}, not {@code MAX}, per bucket</h2>
 *
 * <p>A gauge sample is already an instantaneous level (not a duration or a count to sum/weight-
 * average the way {@link SeriesService} re-buckets a folded measure); averaging the samples that
 * land in one coarser output bucket is the level metric's honest re-bucketing -- it represents the
 * bucket's typical level. {@code MAX} would systematically pull every bucket toward its single
 * highest sample, which overstates sustained WIP for a spiky-but-otherwise-normal window.
 *
 * <h2>No process filter: sum first, then average</h2>
 *
 * <p>{@code processId == null} asks for the system-wide total across every process. Summing the raw
 * rows directly per output bucket would double count: a single sample instant can have several
 * processes' rows, and a coarse bucket can span several sample instants. This first sums every
 * process's gauge <b>per sample instant</b> (the true system-wide level at that instant), then
 * bucket-averages those per-instant sums -- the same average-of-levels rule as the single-process
 * case, just applied to the already-summed level.
 */
@Service
public class GaugeSeriesService {

  private static final String TABLE = "open_instances_gauge";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public GaugeSeriesService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public SeriesResult series(final GaugeSeriesQuery query) {
    if (query.grainMinutes() == null || query.grainMinutes() <= 0) {
      throw new IllegalArgumentException("grainMinutes must be a positive number of minutes");
    }
    final List<String> executedSql = new ArrayList<>();
    if (!viewRegistry.ensureAvailable(TABLE)) {
      return new SeriesResult(List.of(), executedSql);
    }

    final String whereSql =
        "sampled_at >= "
            + SqlText.timestamptzLiteral(query.from())
            + " AND sampled_at < "
            + SqlText.timestamptzLiteral(query.to())
            + " AND "
            + (query.processId() == null
                ? "TRUE"
                : "process_id = " + SqlText.literal(query.processId()));
    final String bucketExpr =
        "time_bucket(INTERVAL '" + query.grainMinutes() + " minutes', sampled_at)";

    final String sql =
        query.processId() != null
            ? "SELECT "
                + bucketExpr
                + " AS t, AVG(open_instances) AS value FROM "
                + SqlText.identifier(TABLE)
                + " WHERE "
                + whereSql
                + " GROUP BY t ORDER BY t"
            : "WITH per_instant AS (SELECT sampled_at, SUM(open_instances) AS total_open FROM "
                + SqlText.identifier(TABLE)
                + " WHERE "
                + whereSql
                + " GROUP BY sampled_at) SELECT "
                + bucketExpr
                + " AS t, AVG(total_open) AS value FROM per_instant GROUP BY t ORDER BY t";
    executedSql.add(sql);

    try {
      return new SeriesResult(toPoints(queryService.execute(sql)), executedSql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Gauge series query failed: " + e.getMessage(), e);
    }
  }

  private List<SeriesPoint> toPoints(final QueryResult result) {
    final List<SeriesPoint> points = new ArrayList<>(result.rows().size());
    for (final List<Object> row : result.rows()) {
      final String t = SqlText.toIsoString(row.get(0));
      final Object rawValue = row.get(1);
      final Double value = rawValue == null ? null : ((Number) rawValue).doubleValue();
      points.add(new SeriesPoint(t, value));
    }
    return points;
  }
}
