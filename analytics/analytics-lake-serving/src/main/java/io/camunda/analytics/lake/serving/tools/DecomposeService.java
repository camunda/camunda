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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/decompose}: for one dim, how much of the change between a {@code baseline}
 * and a {@code window} period each of that dim's values is responsible for.
 *
 * <h2>Formula</h2>
 *
 * <p>For dim value {@code v}: {@code delta(v) = current(v) - baseline(v)}; {@code weight(v) =
 * SUM(weightColumn) WHERE dim = v AND window} / {@code SUM(weightColumn) WHERE window} (the
 * subgroup's share of total volume <b>in the current window</b> — not an average across both
 * periods, so a subgroup that grew large only recently still gets credit proportional to its
 * present-day weight); {@code contributionShare(v) = (delta(v) * weight(v)) / aggregateDelta},
 * where {@code aggregateDelta} is the same current-vs-baseline delta computed with no {@code GROUP
 * BY} at all (the whole entity, filters applied, collapsed to one number per period). {@code
 * weightColumn} is {@code cnt} when the entity has a bare row count, else the measure's own {@code
 * _cnt} column — for a {@code measure == null} (bare count) query, {@code weightColumn} is {@code
 * cnt} and doubles as the value itself.
 *
 * <p>A dim value present in only one of the two periods has no {@code delta} and is skipped (not
 * reported as a zero) — appearing/disappearing entirely is a different kind of finding than "this
 * subgroup's average shifted", and folding it in would silently misattribute contribution share.
 * {@code aggregateDelta == 0} makes every {@code contributionShare} {@code 0.0} rather than a
 * division artifact (NaN/Infinity).
 */
@Service
public class DecomposeService {

  private final MetricRegistry metricRegistry;
  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public DecomposeService(
      final MetricRegistry metricRegistry,
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService) {
    this.metricRegistry = metricRegistry;
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public DecomposeResult decompose(final DecomposeQuery query) {
    final EntityCatalog entity = metricRegistry.require(query.entity());
    final boolean needsHist = query.measure() != null && query.quantile() != null;
    if (needsHist) {
      viewRegistry.ensureAvailable(entity.metricsView(), entity.histView());
    } else {
      viewRegistry.ensureAvailable(entity.metricsView());
    }
    if (!entity.dimNames().contains(query.dim())) {
      throw new IllegalArgumentException(
          "Unknown dim '" + query.dim() + "' for entity '" + entity.name() + "'");
    }
    final MeasureCatalog measure = requireMeasureIfSet(entity, query.measure());
    if (needsHist && !measure.hasHist()) {
      throw new IllegalArgumentException(
          "Measure '"
              + measure.name()
              + "' on entity '"
              + entity.name()
              + "' has no histogram data");
    }
    final String weightColumn = entity.hasCnt() ? "cnt" : measure.name() + "_cnt";
    final String filterSql = FilterClause.toSql(query.filters(), entity.dimNames());

    final List<String> sql = new ArrayList<>();
    try {
      final String windowWhere = timeWhere(query.window()) + " AND " + filterSql;
      final String baselineWhere = timeWhere(query.baseline()) + " AND " + filterSql;

      final Map<String, Double> currentByDim =
          groupedValue(entity, query.measure(), query.quantile(), query.dim(), windowWhere, sql);
      final Map<String, Double> baselineByDim =
          groupedValue(entity, query.measure(), query.quantile(), query.dim(), baselineWhere, sql);
      final double aggregateCurrent =
          scalarValue(entity, query.measure(), query.quantile(), windowWhere, sql);
      final double aggregateBaseline =
          scalarValue(entity, query.measure(), query.quantile(), baselineWhere, sql);
      final double aggregateDelta = aggregateCurrent - aggregateBaseline;

      final Map<String, Double> weightByDim =
          groupedAggregate(
              entity.metricsView(),
              query.dim(),
              "SUM(" + SqlText.identifier(weightColumn) + ")",
              windowWhere,
              sql);
      final double totalWeight =
          scalarAggregate(
              entity.metricsView(),
              "SUM(" + SqlText.identifier(weightColumn) + ")",
              windowWhere,
              sql);

      final List<DecomposeRow> rows = new ArrayList<>();
      for (final String dimValue : currentByDim.keySet()) {
        if (!baselineByDim.containsKey(dimValue)) {
          continue; // present only in the current window -- see class javadoc.
        }
        final double current = currentByDim.get(dimValue);
        final double baseline = baselineByDim.get(dimValue);
        final double delta = current - baseline;
        final double weight =
            totalWeight == 0 ? 0.0 : weightByDim.getOrDefault(dimValue, 0.0) / totalWeight;
        final double contributionShare =
            aggregateDelta == 0 ? 0.0 : (delta * weight) / aggregateDelta;
        rows.add(new DecomposeRow(dimValue, current, baseline, delta, contributionShare, weight));
      }
      rows.sort(
          (a, b) ->
              Double.compare(Math.abs(b.contributionShare()), Math.abs(a.contributionShare())));
      return new DecomposeResult(rows, sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Decompose query failed: " + e.getMessage(), e);
    }
  }

  private MeasureCatalog requireMeasureIfSet(final EntityCatalog entity, final String measureName) {
    if (measureName == null) {
      return null;
    }
    return entity
        .measure(measureName)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown measure '" + measureName + "' for entity '" + entity.name() + "'"));
  }

  private String timeWhere(final TimeRange range) {
    return "window_start >= "
        + SqlText.timestamptzLiteral(range.from())
        + " AND window_start < "
        + SqlText.timestamptzLiteral(range.to());
  }

  private Map<String, Double> groupedValue(
      final EntityCatalog entity,
      final String measureName,
      final Double quantile,
      final String dim,
      final String whereSql,
      final List<String> sql)
      throws SQLException {
    if (measureName == null) {
      return groupedAggregate(entity.metricsView(), dim, "SUM(cnt)", whereSql, sql);
    }
    if (quantile == null) {
      return groupedAggregate(entity.metricsView(), dim, avgExpr(measureName), whereSql, sql);
    }
    return histGrouped(entity, measureName, quantile, SqlText.identifier(dim), whereSql, sql);
  }

  private double scalarValue(
      final EntityCatalog entity,
      final String measureName,
      final Double quantile,
      final String whereSql,
      final List<String> sql)
      throws SQLException {
    if (measureName == null) {
      return scalarAggregate(entity.metricsView(), "SUM(cnt)", whereSql, sql);
    }
    if (quantile == null) {
      return scalarAggregate(entity.metricsView(), avgExpr(measureName), whereSql, sql);
    }
    final Map<String, Double> single =
        histGrouped(entity, measureName, quantile, "'ALL'", whereSql, sql);
    return single.getOrDefault("ALL", 0.0);
  }

  private Map<String, Double> histGrouped(
      final EntityCatalog entity,
      final String measureName,
      final double quantile,
      final String groupExprSql,
      final String whereSql,
      final List<String> sql)
      throws SQLException {
    final Optional<String> scheme =
        HistBinWalk.resolveScheme(queryService, entity.histView(), measureName, whereSql, sql);
    if (scheme.isEmpty()) {
      return Map.of();
    }
    final String query =
        HistBinWalk.sql(
            entity.histView(), measureName, scheme.get(), groupExprSql, "grp", whereSql, quantile);
    sql.add(query);
    return toMap(queryService.execute(query));
  }

  private static String avgExpr(final String measureName) {
    final String cnt = SqlText.identifier(measureName + "_cnt");
    final String sum = SqlText.identifier(measureName + "_sum");
    return "CASE WHEN SUM("
        + cnt
        + ") = 0 THEN NULL ELSE CAST(SUM("
        + sum
        + ") AS DOUBLE) / SUM("
        + cnt
        + ") END";
  }

  private Map<String, Double> groupedAggregate(
      final String view,
      final String dim,
      final String valueExpr,
      final String whereSql,
      final List<String> sql)
      throws SQLException {
    final String query =
        "SELECT "
            + SqlText.identifier(dim)
            + " AS grp, "
            + valueExpr
            + " AS value FROM "
            + SqlText.identifier(view)
            + " WHERE "
            + whereSql
            + " GROUP BY grp";
    sql.add(query);
    return toMap(queryService.execute(query));
  }

  private double scalarAggregate(
      final String view, final String valueExpr, final String whereSql, final List<String> sql)
      throws SQLException {
    final String query =
        "SELECT " + valueExpr + " AS value FROM " + SqlText.identifier(view) + " WHERE " + whereSql;
    sql.add(query);
    final QueryResult result = queryService.execute(query);
    if (result.rows().isEmpty() || result.rows().get(0).get(0) == null) {
      return 0.0;
    }
    return ((Number) result.rows().get(0).get(0)).doubleValue();
  }

  private Map<String, Double> toMap(final QueryResult result) {
    final Map<String, Double> map = new LinkedHashMap<>();
    for (final List<Object> row : result.rows()) {
      final Object value = row.get(1);
      if (value == null) {
        continue; // no value for this group -- treated the same as "not present" by callers.
      }
      map.put(String.valueOf(row.get(0)), ((Number) value).doubleValue());
    }
    return map;
  }

  /**
   * One {@code POST /api/tools/decompose} result row. {@code weight} (this subgroup's share of
   * total current-window volume, the same value the {@code contributionShare} formula itself uses)
   * is one field beyond the API contract's documented shape ({@code value, current, baseline,
   * delta, contributionShare}) -- an optional, additive field {@code POST /api/investigate} reuses
   * as a finding's {@code support}/coverage score, harmless for any caller that ignores it.
   */
  public record DecomposeRow(
      String value,
      double current,
      double baseline,
      double delta,
      double contributionShare,
      double weight) {}

  /** {@code POST /api/tools/decompose} response. */
  public record DecomposeResult(List<DecomposeRow> rows, List<String> sql) {}
}
