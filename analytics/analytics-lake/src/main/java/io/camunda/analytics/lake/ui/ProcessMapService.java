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
import java.util.Optional;

/**
 * DuckDB query logic backing the {@code /api/process-map} endpoint (see {@link LakeUiServer}'s
 * "Process map" section): per-element execution/duration node stats, per-edge transition stats, and
 * the bitmask-driven "only instances that skipped/visited element X" cohort filter.
 *
 * <h2>Cohort filtering (the demo's conformance-checking feature)</h2>
 *
 * <p>{@code instance_kpis.elements_seen} is a bitmask over {@code element_bits}' per-{@code
 * process_definition_key} bit assignment (see {@code GoldTables}' own class javadoc). {@link
 * #resolveCohort} turns a {@code (processId, mode, element)} request into a plain SQL predicate
 * over that bitmask — {@code (elements_seen & (1::BIGINT << bit)) <> 0} for "visited", {@code = 0}
 * for "skipped" — which {@link #nodeStats} then applies via an {@code instance_key} join into
 * {@code activities}. Rows whose {@code elements_seen} is {@code NULL} (more than 64 distinct
 * elements for that definition, see {@code GoldTables}) are excluded from either cohort — the
 * bitmask simply cannot answer "did this instance visit X" for them — but still count toward {@link
 * #totalInstances}.
 *
 * <h2>Edge stats stay cohort-unfiltered</h2>
 *
 * <p>{@link #edgeStats} never applies the cohort predicate: {@code transitions} is already a
 * pre-aggregated gold table (one row per {@code (from_element, to_element)} pair, summed across
 * every instance), not one row per instance, so there is nothing to join a cohort's instance-key
 * set against without re-deriving the whole table from {@code activities} — out of scope for this
 * PoC. The {@code /process-map} page marks the edge layer "all instances" for exactly this reason
 * (see this feature's design brief), so cohort filtering never silently applies to only half the
 * overlay.
 *
 * <h2>Definition-key ambiguity</h2>
 *
 * <p>A {@code process_id} can have more than one {@code process_definition_key} (multiple deployed
 * versions). {@link #resolveProcessDefinitionKey} picks the one with the most {@code instance_kpis}
 * rows — a deliberate PoC simplification (documented, not hidden): the {@code /process-map} page
 * shows one BPMN diagram per process id, not per version, so some ambiguity is unavoidable without
 * a version picker this PoC does not have.
 */
final class ProcessMapService {

  private ProcessMapService() {}

  /** One row of {@link #nodeStats}. */
  record NodeStat(String elementId, long executionCount, Double avgDurationMs) {}

  /** One row of {@link #edgeStats}. */
  record EdgeStat(String fromElement, String toElement, long n, Double avgGapMs) {}

  /** One row of {@link #cohortOptions} — the {@code /process-map} page's cohort-filter dropdown. */
  record CohortOption(String elementId, int bit) {}

  /**
   * Outcome of {@link #resolveCohort}.
   *
   * @param available {@code false} means {@code element} could not be resolved to a bit for this
   *     process (unknown element, or no {@code element_bits} row at all yet) — {@link #nodeStats}
   *     must not be called with this
   * @param predicateSql a boolean SQL expression over {@code instance_kpis} columns, or {@code
   *     null} for {@code mode == "all"} (no filtering)
   */
  record CohortResolution(
      String mode,
      String element,
      boolean available,
      String unavailableReason,
      long totalInstances,
      long cohortInstances,
      String predicateSql) {}

  static List<String> dataProcessIds(final Connection duckdb, final String instanceKpisView)
      throws SQLException {
    final List<String> ids = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT DISTINCT process_id FROM " + instanceKpisView + " ORDER BY 1")) {
      while (rs.next()) {
        ids.add(rs.getString(1));
      }
    }
    return ids;
  }

  /** See class javadoc's "Definition-key ambiguity" section. */
  static Optional<Long> resolveProcessDefinitionKey(
      final Connection duckdb, final String instanceKpisView, final String processId)
      throws SQLException {
    final String sql =
        "SELECT process_definition_key, count(*) AS n FROM "
            + instanceKpisView
            + " WHERE process_id = "
            + quote(processId)
            + " GROUP BY process_definition_key ORDER BY n DESC LIMIT 1";
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      if (rs.next()) {
        return Optional.of(rs.getLong(1));
      }
    }
    return Optional.empty();
  }

  static List<CohortOption> cohortOptions(
      final Connection duckdb, final String elementBitsView, final long processDefinitionKey)
      throws SQLException {
    final List<CohortOption> options = new ArrayList<>();
    final String sql =
        "SELECT element_id, bit FROM "
            + elementBitsView
            + " WHERE process_definition_key = "
            + processDefinitionKey
            + " ORDER BY element_id";
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        options.add(new CohortOption(rs.getString(1), rs.getInt(2)));
      }
    }
    return options;
  }

  /**
   * @param mode {@code "all"}, {@code "skip"} or {@code "visited"}
   * @param element the element id to skip/visit-filter on; ignored when {@code mode} is {@code
   *     "all"}
   */
  static CohortResolution resolveCohort(
      final Connection duckdb,
      final String instanceKpisView,
      final String elementBitsView,
      final String processId,
      final String mode,
      final String element)
      throws SQLException {
    final long totalInstances = countInstances(duckdb, instanceKpisView, processId, null);
    if (!"skip".equals(mode) && !"visited".equals(mode)) {
      return new CohortResolution("all", null, true, null, totalInstances, totalInstances, null);
    }
    if (element == null || element.isBlank()) {
      return new CohortResolution(
          mode,
          element,
          false,
          "no element specified for cohort mode " + mode,
          totalInstances,
          0,
          null);
    }
    final Optional<Long> defKey = resolveProcessDefinitionKey(duckdb, instanceKpisView, processId);
    if (defKey.isEmpty()) {
      return new CohortResolution(
          mode, element, false, "no data for process " + processId, totalInstances, 0, null);
    }
    final Integer bit = findBit(duckdb, elementBitsView, defKey.get(), element);
    if (bit == null) {
      return new CohortResolution(
          mode,
          element,
          false,
          "element " + element + " not found for this process definition",
          totalInstances,
          0,
          null);
    }
    final String bitTest = "(elements_seen & (1::BIGINT << " + bit + "))";
    final String predicate =
        "elements_seen IS NOT NULL AND " + bitTest + ("visited".equals(mode) ? " <> 0" : " = 0");
    final long cohortInstances = countInstances(duckdb, instanceKpisView, processId, predicate);
    return new CohortResolution(
        mode, element, true, null, totalInstances, cohortInstances, predicate);
  }

  private static Integer findBit(
      final Connection duckdb,
      final String elementBitsView,
      final long processDefinitionKey,
      final String elementId)
      throws SQLException {
    final String sql =
        "SELECT bit FROM "
            + elementBitsView
            + " WHERE process_definition_key = "
            + processDefinitionKey
            + " AND element_id = "
            + quote(elementId);
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      return rs.next() ? rs.getInt(1) : null;
    }
  }

  private static long countInstances(
      final Connection duckdb,
      final String instanceKpisView,
      final String processId,
      final String extraPredicate)
      throws SQLException {
    final String sql =
        "SELECT count(*) FROM "
            + instanceKpisView
            + " WHERE process_id = "
            + quote(processId)
            + (extraPredicate == null ? "" : " AND " + extraPredicate);
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
    }
  }

  /** Node badges: execution count + avg duration per element, over {@code cohort}'s instances. */
  static List<NodeStat> nodeStats(
      final Connection duckdb,
      final String activitiesView,
      final String instanceKpisView,
      final String processId,
      final CohortResolution cohort)
      throws SQLException {
    final String sql;
    if (cohort.predicateSql() == null) {
      sql =
          "SELECT element_id, count(*) AS execution_count, avg(duration_ms) AS avg_duration_ms "
              + "FROM "
              + activitiesView
              + " WHERE process_id = "
              + quote(processId)
              + " GROUP BY element_id";
    } else {
      sql =
          "WITH cohort AS (SELECT instance_key FROM "
              + instanceKpisView
              + " WHERE process_id = "
              + quote(processId)
              + " AND "
              + cohort.predicateSql()
              + ") "
              + "SELECT a.element_id, count(*) AS execution_count, avg(a.duration_ms) AS avg_duration_ms "
              + "FROM "
              + activitiesView
              + " a JOIN cohort c ON a.instance_key = c.instance_key "
              + "WHERE a.process_id = "
              + quote(processId)
              + " GROUP BY a.element_id";
    }
    final List<NodeStat> stats = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        stats.add(new NodeStat(rs.getString(1), rs.getLong(2), (Double) rs.getObject(3)));
      }
    }
    return stats;
  }

  /**
   * Edge badges/coloring: {@code n} + avg gap per directly-follows edge -- always all instances.
   */
  static List<EdgeStat> edgeStats(
      final Connection duckdb, final String transitionsView, final String processId)
      throws SQLException {
    final String sql =
        "SELECT from_element, to_element, CAST(sum(n) AS BIGINT) AS n, "
            + "sum(total_gap_ms)::DOUBLE / sum(n) AS avg_gap_ms "
            + "FROM "
            + transitionsView
            + " WHERE process_id = "
            + quote(processId)
            + " AND from_element <> '__START__' AND to_element <> '__END__' "
            + "GROUP BY from_element, to_element";
    final List<EdgeStat> stats = new ArrayList<>();
    try (Statement statement = duckdb.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      while (rs.next()) {
        stats.add(
            new EdgeStat(
                rs.getString(1), rs.getString(2), rs.getLong(3), (Double) rs.getObject(4)));
      }
    }
    return stats;
  }

  private static String quote(final String value) {
    return "'" + value.replace("'", "''") + "'";
  }
}
