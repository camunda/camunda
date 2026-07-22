/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DuckDB query logic backing the {@code /api/process-map} endpoint (see {@link LakeUiServer}'s
 * "Process map" section): per-element execution/duration node stats derived from the raw {@code
 * activities} table.
 */
final class ProcessMapService {

  private ProcessMapService() {}

  /** One row of {@link #nodeStats}. */
  record NodeStat(String elementId, long executionCount, Double avgDurationMs) {}

  static List<String> dataProcessIds(final Connection duckdb, final String activitiesView)
      throws SQLException {
    final List<String> ids = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT DISTINCT process_id FROM " + activitiesView + " ORDER BY 1")) {
      while (rs.next()) {
        ids.add(rs.getString(1));
      }
    }
    return ids;
  }

  /** Node badges: execution count + avg duration per element, over every instance. */
  static List<NodeStat> nodeStats(
      final Connection duckdb, final String activitiesView, final String processId)
      throws SQLException {
    final String sql =
        "SELECT element_id, count(*) AS execution_count, avg(duration_ms) AS avg_duration_ms "
            + "FROM "
            + activitiesView
            + " WHERE process_id = "
            + quote(processId)
            + " GROUP BY element_id";
    final List<NodeStat> stats = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        stats.add(new NodeStat(rs.getString(1), rs.getLong(2), (Double) rs.getObject(3)));
      }
    }
    return stats;
  }

  private static String quote(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }
}
