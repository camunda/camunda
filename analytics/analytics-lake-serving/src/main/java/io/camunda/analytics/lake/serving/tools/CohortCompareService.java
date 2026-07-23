/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import io.camunda.analytics.lake.serving.catalog.EntityCatalog;
import io.camunda.analytics.lake.serving.catalog.MetricRegistry;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService;
import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.duckdb.LakeViewRegistry;
import io.camunda.analytics.lake.serving.sql.FilterClause;
import io.camunda.analytics.lake.serving.sql.SqlText;
import io.camunda.analytics.lake.serving.sql.ViewDescribe;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/cohort-compare}: one single-scan {@code GROUP BY} over the {@code
 * instances} raw view (v1 supports only this entity — the auto-attribute derivation below is
 * specific to its {@code variant_hash}/{@code vars_json} shape) cross-tabulating a cohort predicate
 * against every requested attribute at once, then marginalizing per attribute in Java rather than
 * running one query per attribute.
 *
 * <h2>Auto attributes</h2>
 *
 * <p>{@code variant_hash}, plus the {@code instances} entity's own registry dims (today: {@code
 * process_id}), plus one bucketed attribute per numeric variable found in {@code
 * variable_profiles_hist} (skipped entirely if that view isn't registered): each variable's global
 * quartile boundaries (over the same window/filters) are read via {@link HistBinWalk}, then every
 * instance's own value for that variable — extracted from {@code vars_json} with {@code
 * json_extract} — is bucketed into {@code Q1}..{@code Q4} by a {@code CASE WHEN} against those
 * boundaries, right inside the one scan.
 *
 * <h2>Row semantics</h2>
 *
 * <p>{@code slowShare(attr, bucket) = slowN(attr, bucket) / totalSlowN(attr)}, where {@code
 * totalSlowN(attr)} sums over every non-null bucket of that same attribute (an instance missing a
 * variable simply doesn't contribute to that variable's attribute, rather than skewing its
 * denominator) — same for {@code fastShare}. {@code lift = slowShare / fastShare}; when {@code
 * fastShare} is exactly 0 (a bucket that only ever appears in the slow cohort), {@code lift} is
 * reported as {@code null} (undefined/unbounded) rather than {@code Infinity}, which isn't valid
 * JSON. Rows with {@code slowN < supportFloor} are dropped; the remaining rows are sorted by {@code
 * |lift - 1|} descending (undefined lift sorts last) as a reasonable default view, though the
 * contract doesn't mandate an order.
 */
@Service
public class CohortCompareService {

  private static final int DEFAULT_SUPPORT_FLOOR = 20;
  private static final String INSTANCES = "instances";
  private static final String VARIABLE_PROFILES_HIST = "variable_profiles_hist";

  /**
   * The one documented internal row-cap override this module uses (see {@link
   * LakeQueryService#execute(String, int)}'s own javadoc): the single combined scan's row count is
   * bounded by attribute/bucket cardinality (cohort flag x every attribute's distinct values),
   * which can legitimately exceed the public API's default 500-row cap without indicating a runaway
   * query. Still a finite bound, not {@code Integer.MAX_VALUE} -- a pathologically high-cardinality
   * attribute set should fail loudly rather than exhaust heap.
   */
  private static final int SCAN_ROW_CAP = 200_000;

  private final MetricRegistry metricRegistry;
  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public CohortCompareService(
      final MetricRegistry metricRegistry,
      final LakeViewRegistry viewRegistry,
      final LakeQueryService queryService) {
    this.metricRegistry = metricRegistry;
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public CohortCompareResult compare(final CohortCompareQuery query) {
    if (!INSTANCES.equals(query.entity())) {
      throw new IllegalArgumentException(
          "cohort-compare v1 only supports entity 'instances', got '" + query.entity() + "'");
    }
    viewRegistry.ensureAvailable(INSTANCES);
    final int supportFloor =
        query.supportFloor() == null ? DEFAULT_SUPPORT_FLOOR : query.supportFloor();
    final List<String> sql = new ArrayList<>();

    try {
      final List<String> rawColumns = ViewDescribe.columns(queryService, INSTANCES);
      final String cohortSql = CohortPredicate.toSql(query.cohort(), Set.copyOf(rawColumns));
      final String filterSql = FilterClause.toSql(query.filters(), rawColumns);
      final String whereSql =
          "started_at >= "
              + SqlText.timestamptzLiteral(query.from())
              + " AND started_at < "
              + SqlText.timestamptzLiteral(query.to())
              + " AND "
              + filterSql;

      final List<AttributeSpec> attributes =
          query.attributes() != null
              ? explicitAttributes(query.attributes(), rawColumns)
              : autoAttributes(rawColumns, query.filters(), query.from(), query.to(), sql);

      if (attributes.isEmpty()) {
        return new CohortCompareResult(List.of(), sql);
      }

      final StringBuilder select =
          new StringBuilder("SELECT (").append(cohortSql).append(") AS cohort_flag");
      for (int i = 0; i < attributes.size(); i++) {
        select.append(", ").append(attributes.get(i).sqlExpr()).append(" AS a").append(i);
      }
      select
          .append(", count(*) AS n FROM ")
          .append(SqlText.identifier(INSTANCES))
          .append(" WHERE ")
          .append(whereSql)
          .append(" GROUP BY cohort_flag");
      for (int i = 0; i < attributes.size(); i++) {
        select.append(", a").append(i);
      }
      final String scanSql = select.toString();
      sql.add(scanSql);
      final QueryResult scan = queryService.execute(scanSql, SCAN_ROW_CAP);
      if (scan.truncated()) {
        // Marginalizing a truncated scan would silently under/over-count some (cohort_flag,
        // attribute-combo) groups -- every slowShare/fastShare/lift computed from it would be
        // wrong with no indication, which is worse than refusing outright. Fail loudly instead of
        // reporting skewed numbers (see SCAN_ROW_CAP's own javadoc).
        throw new IllegalArgumentException(
            "cohort-compare's single scan exceeded its "
                + SCAN_ROW_CAP
                + "-row cap ("
                + attributes.size()
                + " attribute(s) x cohort flag x bucket cardinality) -- narrow the window or"
                + " filters, or pass explicit attributes instead of \"auto\", to bring the"
                + " combination count down");
      }

      final List<CohortCompareRow> rows = marginalize(attributes, scan, supportFloor);
      rows.sort(
          (a, b) -> {
            final double liftA = a.lift() == null ? -1 : Math.abs(a.lift() - 1);
            final double liftB = b.lift() == null ? -1 : Math.abs(b.lift() - 1);
            return Double.compare(liftB, liftA);
          });
      return new CohortCompareResult(rows, sql);
    } catch (final SQLException e) {
      throw new IllegalStateException("Cohort-compare query failed: " + e.getMessage(), e);
    }
  }

  private List<AttributeSpec> explicitAttributes(
      final List<String> names, final List<String> rawColumns) {
    final List<AttributeSpec> attributes = new ArrayList<>();
    for (final String name : names) {
      if (!rawColumns.contains(name)) {
        throw new IllegalArgumentException(
            "Unknown attribute '" + name + "' on entity 'instances'");
      }
      attributes.add(new AttributeSpec(name, SqlText.identifier(name)));
    }
    return attributes;
  }

  private List<AttributeSpec> autoAttributes(
      final List<String> rawColumns,
      final Map<String, Object> filters,
      final String from,
      final String to,
      final List<String> sql)
      throws SQLException {
    final List<AttributeSpec> attributes = new ArrayList<>();
    if (rawColumns.contains("variant_hash")) {
      attributes.add(new AttributeSpec("variant_hash", SqlText.identifier("variant_hash")));
    }
    final EntityCatalog instancesEntity =
        metricRegistry.entities().stream()
            .filter(e -> e.name().equals(INSTANCES))
            .findFirst()
            .orElse(null);
    if (instancesEntity != null) {
      for (final String dim : instancesEntity.dimNames()) {
        if (rawColumns.contains(dim) && !"variant_hash".equals(dim)) {
          attributes.add(new AttributeSpec(dim, SqlText.identifier(dim)));
        }
      }
    }
    attributes.addAll(numericVarAttributes(filters, from, to, sql));
    return attributes;
  }

  private List<AttributeSpec> numericVarAttributes(
      final Map<String, Object> filters, final String from, final String to, final List<String> sql)
      throws SQLException {
    if (!viewRegistry.ensureAvailable(VARIABLE_PROFILES_HIST)) {
      return List.of(); // no variable-profile histograms yet -- skip vars entirely (see javadoc).
    }
    final EntityCatalog profiles =
        metricRegistry.entities().stream()
            .filter(e -> e.name().equals("variable_profiles"))
            .findFirst()
            .orElse(null);
    if (profiles == null || profiles.measure("value").isEmpty()) {
      return List.of();
    }
    final Map<String, Object> profileFilters = new LinkedHashMap<>();
    if (filters != null) {
      for (final String dim : profiles.dimNames()) {
        if (filters.containsKey(dim)) {
          profileFilters.put(dim, filters.get(dim));
        }
      }
    }
    final String windowSql =
        "window_start >= "
            + SqlText.timestamptzLiteral(from)
            + " AND window_start < "
            + SqlText.timestamptzLiteral(to)
            + " AND "
            + FilterClause.toSql(profileFilters, profiles.dimNames());

    final String varNamesSql =
        "SELECT DISTINCT var_name FROM "
            + SqlText.identifier("variable_profiles_hist")
            + " WHERE measure = 'value' AND "
            + windowSql;
    sql.add(varNamesSql);
    final QueryResult varNamesResult = queryService.execute(varNamesSql);

    final List<AttributeSpec> attributes = new ArrayList<>();
    for (final List<Object> row : varNamesResult.rows()) {
      final String varName = (String) row.get(0);
      final String varWhere = "var_name = " + SqlText.literal(varName) + " AND " + windowSql;
      final Double q1 = quantile(varWhere, 0.25, sql);
      final Double q2 = quantile(varWhere, 0.50, sql);
      final Double q3 = quantile(varWhere, 0.75, sql);
      if (q1 == null || q2 == null || q3 == null) {
        continue;
      }
      // decode(), not CAST(blob AS VARCHAR): vars_json is a BLOB in production, and DuckDB's
      // blob-to-varchar rendering escapes bytes (including quotes) as \xHH literals, corrupting
      // the JSON before json_extract sees it. typeof-guarded so VARCHAR fixture warehouses still
      // read as-is (same idiom as ProcessDefinitionsService/ConditionsService).
      final String valueExpr =
          "TRY_CAST(json_extract(CASE WHEN typeof(vars_json) = 'BLOB'"
              + " THEN decode(CAST(vars_json AS BLOB)) ELSE CAST(vars_json AS VARCHAR) END, '$."
              + varName.replace("'", "''")
              + "') AS DOUBLE)";
      final String bucketExpr =
          "CASE WHEN "
              + valueExpr
              + " IS NULL THEN NULL WHEN "
              + valueExpr
              + " < "
              + q1
              + " THEN 'Q1' WHEN "
              + valueExpr
              + " < "
              + q2
              + " THEN 'Q2' WHEN "
              + valueExpr
              + " < "
              + q3
              + " THEN 'Q3' ELSE 'Q4' END";
      attributes.add(new AttributeSpec("var:" + varName, bucketExpr));
    }
    return attributes;
  }

  private Double quantile(final String whereSql, final double q, final List<String> sql)
      throws SQLException {
    final Optional<String> scheme =
        HistBinWalk.resolveScheme(queryService, "variable_profiles_hist", "value", whereSql, sql);
    if (scheme.isEmpty()) {
      return null;
    }
    final String query =
        HistBinWalk.sql(
            "variable_profiles_hist", "value", scheme.get(), "'ALL'", "grp", whereSql, q);
    sql.add(query);
    final QueryResult result = queryService.execute(query);
    if (result.rows().isEmpty() || result.rows().get(0).get(1) == null) {
      return null;
    }
    return ((Number) result.rows().get(0).get(1)).doubleValue();
  }

  private List<CohortCompareRow> marginalize(
      final List<AttributeSpec> attributes, final QueryResult scan, final int supportFloor) {
    final List<CohortCompareRow> rows = new ArrayList<>();
    for (int attrIndex = 0; attrIndex < attributes.size(); attrIndex++) {
      final String attrLabel = attributes.get(attrIndex).label();
      final Map<String, long[]> byBucket = new LinkedHashMap<>(); // bucket -> [slowN, fastN]
      for (final List<Object> row : scan.rows()) {
        final Object bucketValue = row.get(1 + attrIndex);
        if (bucketValue == null) {
          continue;
        }
        final boolean slow = Boolean.TRUE.equals(row.get(0));
        final long n = ((Number) row.get(row.size() - 1)).longValue();
        final long[] counts =
            byBucket.computeIfAbsent(String.valueOf(bucketValue), k -> new long[2]);
        counts[slow ? 0 : 1] += n;
      }
      long totalSlow = 0;
      long totalFast = 0;
      for (final long[] counts : byBucket.values()) {
        totalSlow += counts[0];
        totalFast += counts[1];
      }
      for (final Map.Entry<String, long[]> entry : byBucket.entrySet()) {
        final long slowN = entry.getValue()[0];
        final long fastN = entry.getValue()[1];
        if (slowN < supportFloor) {
          continue;
        }
        final double slowShare = totalSlow == 0 ? 0.0 : (double) slowN / totalSlow;
        final double fastShare = totalFast == 0 ? 0.0 : (double) fastN / totalFast;
        final Double lift = fastShare == 0.0 ? null : slowShare / fastShare;
        rows.add(
            new CohortCompareRow(
                attrLabel, entry.getKey(), slowShare, fastShare, lift, slowN, fastN));
      }
    }
    return rows;
  }

  private record AttributeSpec(String label, String sqlExpr) {}

  /** One {@code POST /api/tools/cohort-compare} result row. */
  public record CohortCompareRow(
      String attribute,
      String bucket,
      double slowShare,
      double fastShare,
      Double lift,
      long slowN,
      long fastN) {}

  /** {@code POST /api/tools/cohort-compare} response. */
  public record CohortCompareResult(List<CohortCompareRow> rows, List<String> sql) {}
}
