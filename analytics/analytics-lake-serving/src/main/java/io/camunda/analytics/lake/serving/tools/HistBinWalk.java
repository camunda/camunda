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
import io.camunda.analytics.lake.serving.sql.SqlText;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * The cumulative-count percentile bin-walk over a {@code _hist} partials view, generalized to an
 * arbitrary grouping expression (a time bucket for {@code /api/tools/series}, a dim column for
 * {@code /api/tools/decompose}) — the same algorithm {@code analytics-lake}'s own {@code
 * ExpHistogramAlgebra#finalizeQuery} uses for its fixed dims/window grouping, reimplemented here
 * (this module has no dependency on {@code analytics-lake}'s code, only its Parquet output):
 * cumulative-sum bins ordered by {@code bin_lo} within each group, reporting the midpoint of the
 * first bin whose running count reaches {@code quantile * group_total_count}.
 *
 * <p>Bins from different histogram <b>schemes</b> are never comparable (different bucketing math),
 * so every bin-walk first pins down a single {@code scheme} via {@link #resolveScheme} — this is a
 * v1 simplification: a measure that (unexpectedly) has more than one scheme in the requested window
 * picks the lexicographically first one rather than erroring, since a single declared measure using
 * two different schemes at once isn't expected to happen in practice.
 */
public final class HistBinWalk {

  private HistBinWalk() {}

  /**
   * The single {@code scheme} value to bin-walk with, or empty if the measure has no histogram rows
   * at all in the given (already-filtered, scheme-less) {@code whereSql}.
   */
  public static Optional<String> resolveScheme(
      final LakeQueryService queryService,
      final String histView,
      final String measure,
      final String whereSql,
      final List<String> executedSql)
      throws SQLException {
    final String sql =
        "SELECT DISTINCT scheme FROM "
            + SqlText.identifier(histView)
            + " WHERE measure = "
            + SqlText.literal(measure)
            + " AND "
            + whereSql
            + " ORDER BY scheme";
    executedSql.add(sql);
    final QueryResult result = queryService.execute(sql);
    if (result.rows().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of((String) result.rows().get(0).get(0));
  }

  /**
   * Full bin-walk SQL text estimating {@code quantile} (a literal {@code [0,1]} value, already
   * validated by the caller) per {@code groupExprSql}-produced group, aliased {@code groupAlias} in
   * the result (selected alongside a {@code value} column).
   */
  public static String sql(
      final String histView,
      final String measure,
      final String scheme,
      final String groupExprSql,
      final String groupAlias,
      final String whereSql,
      final double quantile) {
    final String quotedAlias = SqlText.identifier(groupAlias);
    return "WITH raw AS (SELECT "
        + groupExprSql
        + " AS "
        + quotedAlias
        + ", bin_lo, bin_hi, cnt FROM "
        + SqlText.identifier(histView)
        + " WHERE measure = "
        + SqlText.literal(measure)
        + " AND scheme = "
        + SqlText.literal(scheme)
        + " AND "
        + whereSql
        + "), merged AS (SELECT "
        + quotedAlias
        + ", bin_lo, bin_hi, SUM(cnt) AS cnt FROM raw GROUP BY "
        + quotedAlias
        + ", bin_lo, bin_hi), ranked AS (SELECT "
        + quotedAlias
        + ", bin_lo, bin_hi, cnt, SUM(cnt) OVER (PARTITION BY "
        + quotedAlias
        + " ORDER BY bin_lo ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cum_cnt, SUM(cnt)"
        + " OVER (PARTITION BY "
        + quotedAlias
        + ") AS total_cnt FROM merged) SELECT "
        + quotedAlias
        + ", MIN(CASE WHEN cum_cnt >= total_cnt * "
        + quantile
        + " THEN (bin_lo + bin_hi) / 2.0 END) AS value FROM ranked GROUP BY "
        + quotedAlias
        + " ORDER BY "
        + quotedAlias;
  }
}
