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
 * {@code POST /api/tools/cohort-share}: per output bucket, what share of an entity's histogram mass
 * falls at or below each of a set of caller-chosen thresholds -- e.g. the Cohort survival tile's
 * "what share of this start-hour cohort completed within 1h / within 1d".
 *
 * <p>This has no {@code tools/series} equivalent: {@code series}'s quantile mode (see {@link
 * HistBinWalk}) answers "what value is at percentile p" (a bin-walk that stops at a caller-chosen
 * <em>rank</em>); this tool answers the inverse -- "what fraction of the mass sits at or below a
 * caller-chosen <em>value</em>" -- which is a single {@code SUM(CASE WHEN bin_hi <= threshold ...)}
 * per bucket, not a cumulative-rank walk. Both read the same {@code _hist} partials table and
 * resolve a single {@code scheme} the same way ({@link HistBinWalk#resolveScheme}); this tool then
 * builds its own share-shaped SQL rather than reusing {@link HistBinWalk#sql}, since that method's
 * shape (bin-walk to a rank) doesn't fit a share-below-a-value query.
 *
 * <p>A missing {@code _hist} view degrades to one empty-points {@link ShareSeries} per requested
 * threshold, never an error -- the Cohort survival tile renders its own "not available yet" either
 * way.
 */
@Service
public class CohortShareService {

  private final MetricRegistry metricRegistry;
  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public CohortShareService(
      final MetricRegistry metricRegistry,
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService) {
    this.metricRegistry = metricRegistry;
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public CohortShareResult share(final CohortShareQuery query) {
    if (query.grainMinutes() == null || query.grainMinutes() <= 0) {
      throw new IllegalArgumentException("grainMinutes must be a positive number of minutes");
    }
    if (query.thresholdsMs() == null || query.thresholdsMs().isEmpty()) {
      throw new IllegalArgumentException("thresholdsMs must have at least one value");
    }
    final String histView = query.entity() + "_hist";
    final List<String> executedSql = new ArrayList<>();
    if (!viewRegistry.ensureAvailable(histView)) {
      return new CohortShareResult(emptySeries(query.thresholdsMs()), executedSql);
    }

    final EntityCatalog entity = metricRegistry.require(query.entity());
    if (entity.measures().isEmpty()) {
      throw new IllegalArgumentException("Entity '" + entity.name() + "' has no measures");
    }
    // This tool's contract has no measure field of its own (unlike series/decompose) -- the same
    // "obvious measure" default those tools apply when a quantile is requested with no explicit
    // measure name.
    final MeasureCatalog measure = entity.measures().get(0);
    if (!measure.hasHist()) {
      throw new IllegalArgumentException(
          "Measure '"
              + measure.name()
              + "' on entity '"
              + entity.name()
              + "' has no histogram data");
    }

    final String filterSql = FilterClause.toSql(query.filters(), entity.dimNames());
    final String windowSql =
        "window_start >= "
            + SqlText.timestamptzLiteral(query.from())
            + " AND window_start < "
            + SqlText.timestamptzLiteral(query.to());
    final String whereSql = windowSql + " AND " + filterSql;

    try {
      final Optional<String> scheme =
          HistBinWalk.resolveScheme(queryService, histView, measure.name(), whereSql, executedSql);
      if (scheme.isEmpty()) {
        return new CohortShareResult(emptySeries(query.thresholdsMs()), executedSql);
      }

      final String bucketExpr =
          "time_bucket(INTERVAL '" + query.grainMinutes() + " minutes', window_start)";
      final List<String> withinColumns = new ArrayList<>();
      for (int i = 0; i < query.thresholdsMs().size(); i++) {
        withinColumns.add(
            "SUM(CASE WHEN bin_hi <= "
                + query.thresholdsMs().get(i)
                + " THEN cnt ELSE 0 END) AS within_"
                + i);
      }
      final String sql =
          "SELECT "
              + bucketExpr
              + " AS t, "
              + String.join(", ", withinColumns)
              + ", SUM(cnt) AS total FROM "
              + SqlText.identifier(histView)
              + " WHERE measure = "
              + SqlText.literal(measure.name())
              + " AND scheme = "
              + SqlText.literal(scheme.get())
              + " AND "
              + whereSql
              + " GROUP BY t ORDER BY t";
      executedSql.add(sql);

      final QueryResult result = queryService.execute(sql);
      final List<List<SharePoint>> pointsByThreshold = new ArrayList<>();
      for (int i = 0; i < query.thresholdsMs().size(); i++) {
        pointsByThreshold.add(new ArrayList<>());
      }
      final int totalColumn = query.thresholdsMs().size() + 1;
      for (final List<Object> row : result.rows()) {
        final String t = SqlText.toIsoString(row.get(0));
        final double total = ((Number) row.get(totalColumn)).doubleValue();
        for (int i = 0; i < query.thresholdsMs().size(); i++) {
          final double within = ((Number) row.get(i + 1)).doubleValue();
          final Double share = total > 0 ? within / total : null;
          pointsByThreshold.get(i).add(new SharePoint(t, share));
        }
      }

      final List<ShareSeries> series = new ArrayList<>();
      for (int i = 0; i < query.thresholdsMs().size(); i++) {
        series.add(new ShareSeries(query.thresholdsMs().get(i), pointsByThreshold.get(i)));
      }
      return new CohortShareResult(series, executedSql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Cohort-share query failed: " + e.getMessage(), e);
    }
  }

  private List<ShareSeries> emptySeries(final List<Long> thresholdsMs) {
    final List<ShareSeries> series = new ArrayList<>();
    for (final Long threshold : thresholdsMs) {
      series.add(new ShareSeries(threshold, List.of()));
    }
    return series;
  }
}
