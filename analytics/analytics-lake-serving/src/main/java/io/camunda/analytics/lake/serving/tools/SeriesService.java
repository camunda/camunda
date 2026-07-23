/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.catalog.EntityCatalog;
import io.camunda.analytics.lake.serving.catalog.MeasureCatalog;
import io.camunda.analytics.lake.serving.catalog.MetricRegistry;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.FilterClause;
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Computes one aggregated time series over an entity's partials tables — the shared engine behind
 * {@code POST /api/tools/series}, {@code .../changepoint}, and each candidate in {@code
 * .../screen}.
 *
 * <h2>Value semantics</h2>
 *
 * <ul>
 *   <li>{@code measure == null}: {@code SUM(cnt)} per output bucket (the entity's plain row count).
 *   <li>{@code measure} set, {@code quantile == null}: the bucket's weighted average, {@code
 *       CAST(SUM(measure_sum) AS DOUBLE) / SUM(measure_cnt)} — the statistically correct way to
 *       re-aggregate partial sums/counts up to a coarser bucket than the stored window, not a mean
 *       of already-averaged values.
 *   <li>{@code measure} set, {@code quantile} set: a cumulative-count bin-walk over the matching
 *       {@code _hist} view (see {@link HistBinWalk}), re-bucketed the same way.
 * </ul>
 */
@Service
public class SeriesService {

  private final MetricRegistry metricRegistry;
  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public SeriesService(
      final MetricRegistry metricRegistry,
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService) {
    this.metricRegistry = metricRegistry;
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public SeriesResult series(final SeriesQuery query) {
    final EntityCatalog entity = metricRegistry.require(query.entity());
    // The client names the bare row count as measure "cnt" (that's how the registry surfaces it);
    // normalize to the null-measure count path — both spellings mean SUM(cnt).
    final String measureName = "cnt".equals(query.measure()) ? null : query.measure();
    final boolean needsHist = measureName != null && query.quantile() != null;
    if (needsHist) {
      viewRegistry.ensureAvailable(entity.metricsView(), entity.histView());
    } else {
      viewRegistry.ensureAvailable(entity.metricsView());
    }
    if (query.grainMinutes() == null || query.grainMinutes() <= 0) {
      throw new IllegalArgumentException("grainMinutes must be a positive number of minutes");
    }
    final String filterSql = FilterClause.toSql(query.filters(), entity.dimNames());
    final String windowSql =
        "window_start >= "
            + SqlText.timestamptzLiteral(query.from())
            + " AND window_start < "
            + SqlText.timestamptzLiteral(query.to());
    final String whereSql = windowSql + " AND " + filterSql;
    final String bucketExpr =
        "time_bucket(INTERVAL '" + query.grainMinutes() + " minutes', window_start)";

    final List<String> executedSql = new ArrayList<>();
    try {
      if (measureName == null) {
        if (!entity.hasCnt()) {
          throw new IllegalArgumentException(
              "Entity '" + entity.name() + "' has no bare row count (cnt); a measure is required");
        }
        if (query.quantile() != null) {
          throw new IllegalArgumentException(
              "quantile requires a real measure, not the bare row count");
        }
        return new SeriesResult(
            runBucketQuery(entity.metricsView(), bucketExpr, "SUM(cnt)", whereSql, executedSql),
            executedSql);
      }

      final MeasureCatalog measure =
          entity
              .measure(measureName)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Unknown measure '"
                              + measureName
                              + "' for entity '"
                              + entity.name()
                              + "'"));

      if (query.quantile() == null) {
        final String valueExpr =
            "CASE WHEN SUM("
                + SqlText.identifier(measure.name() + "_cnt")
                + ") = 0 THEN NULL ELSE CAST(SUM("
                + SqlText.identifier(measure.name() + "_sum")
                + ") AS DOUBLE) / SUM("
                + SqlText.identifier(measure.name() + "_cnt")
                + ") END";
        return new SeriesResult(
            runBucketQuery(entity.metricsView(), bucketExpr, valueExpr, whereSql, executedSql),
            executedSql);
      }

      if (!measure.hasHist()) {
        throw new IllegalArgumentException(
            "Measure '"
                + measure.name()
                + "' on entity '"
                + entity.name()
                + "' has no histogram data");
      }
      if (query.quantile() < 0 || query.quantile() > 1) {
        throw new IllegalArgumentException("quantile must be within [0, 1]");
      }
      final Optional<String> scheme =
          HistBinWalk.resolveScheme(
              queryService, entity.histView(), measure.name(), whereSql, executedSql);
      if (scheme.isEmpty()) {
        return new SeriesResult(List.of(), executedSql);
      }
      final String sql =
          HistBinWalk.sql(
              entity.histView(),
              measure.name(),
              scheme.get(),
              bucketExpr,
              "t",
              whereSql,
              query.quantile());
      executedSql.add(sql);
      return new SeriesResult(toPoints(queryService.execute(sql)), executedSql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Series query failed: " + e.getMessage(), e);
    }
  }

  private List<SeriesPoint> runBucketQuery(
      final String view,
      final String bucketExpr,
      final String valueExpr,
      final String whereSql,
      final List<String> executedSql)
      throws SQLException {
    final String sql =
        "SELECT "
            + bucketExpr
            + " AS t, "
            + valueExpr
            + " AS value FROM "
            + SqlText.identifier(view)
            + " WHERE "
            + whereSql
            + " GROUP BY t ORDER BY t";
    executedSql.add(sql);
    return toPoints(queryService.execute(sql));
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
