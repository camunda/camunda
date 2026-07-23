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
import io.camunda.analytics.lake.serving.sql.ViewDescribe;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/tools/exemplars}: the {@code k} slowest (or otherwise cohort-matching) concrete
 * instances for a cohort predicate, so a finding can point at real examples rather than only
 * aggregate numbers.
 */
@Service
public class ExemplarsService {

  private static final int DEFAULT_K = 3;
  private static final String INSTANCES = "instances";

  private final LakeViewRegistry viewRegistry;
  private final LakeQueryService queryService;

  public ExemplarsService(
      final LakeViewRegistry viewRegistry, final LakeQueryService queryService) {
    this.viewRegistry = viewRegistry;
    this.queryService = queryService;
  }

  public ExemplarsResult exemplars(final ExemplarsQuery query) {
    if (!INSTANCES.equals(query.entity())) {
      throw new IllegalArgumentException(
          "exemplars v1 only supports entity 'instances', got '" + query.entity() + "'");
    }
    viewRegistry.ensureAvailable(INSTANCES);
    final int k = query.k() == null ? DEFAULT_K : query.k();
    try {
      final List<String> rawColumns = ViewDescribe.columns(queryService, INSTANCES);
      final String cohortSql = CohortPredicate.toSql(query.cohort(), Set.copyOf(rawColumns));
      final String sql =
          "SELECT key, duration_ms, started_at, variant_hash FROM "
              + SqlText.identifier(INSTANCES)
              + " WHERE ("
              + cohortSql
              + ") ORDER BY duration_ms DESC LIMIT "
              + k;
      // k is expected to stay small (a handful of example instances); no need for the internal
      // elevated row-cap path -- the default cap comfortably covers any realistic k.
      final QueryResult result = queryService.execute(sql);
      final List<ExemplarRow> rows = new ArrayList<>(result.rows().size());
      for (final List<Object> row : result.rows()) {
        rows.add(
            new ExemplarRow(
                ((Number) row.get(0)).longValue(),
                row.get(1) == null ? null : ((Number) row.get(1)).longValue(),
                SqlText.toIsoString(row.get(2)),
                (String) row.get(3)));
      }
      return new ExemplarsResult(rows, List.of(sql));
    } catch (final SQLException e) {
      throw new IllegalStateException("Exemplars query failed: " + e.getMessage(), e);
    }
  }

  /** One exemplar instance. */
  public record ExemplarRow(
      long instanceKey, Long durationMs, String startedAt, String variantHash) {}

  /** {@code POST /api/tools/exemplars} response. */
  public record ExemplarsResult(List<ExemplarRow> rows, List<String> sql) {}
}
