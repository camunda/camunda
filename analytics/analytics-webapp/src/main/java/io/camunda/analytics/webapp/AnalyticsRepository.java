/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.webapp.model.Dataset;
import io.camunda.analytics.webapp.model.HeatmapCell;
import io.camunda.analytics.webapp.model.Report;
import io.camunda.analytics.webapp.model.ReportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Stores dataset/report <em>definitions</em> in H2 and runs a report by querying the windowed
 * aggregate table the pipeline produces ({@code proc_inst_exec_time_window}), merging the
 * per-source-partition partials with a {@code GROUP BY} at read time.
 */
@Repository
public class AnalyticsRepository {

  private static final RowMapper<Dataset> DATASET_MAPPER =
      (rs, n) ->
          new Dataset(
              rs.getLong("id"),
              rs.getString("name"),
              rs.getString("fact_type"),
              rs.getString("dimensions"),
              rs.getLong("window_size_ms"));

  private static final RowMapper<Report> REPORT_MAPPER =
      (rs, n) ->
          new Report(
              rs.getLong("id"),
              rs.getString("name"),
              rs.getLong("dataset_id"),
              rs.getString("viz_type"),
              rs.getString("bpmn_process_id"),
              rs.getString("region"),
              (Long) rs.getObject("from_window"),
              (Long) rs.getObject("to_window"));

  private static final RowMapper<ReportRow> REPORT_ROW_MAPPER =
      (rs, n) ->
          new ReportRow(
              rs.getString("region"),
              rs.getString("bpmn_process_id"),
              rs.getLong("window_start"),
              rs.getLong("completed"),
              rs.getDouble("avg_duration"),
              rs.getLong("max_duration"));

  private final JdbcTemplate jdbc;

  public AnalyticsRepository(final JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Dataset> listDatasets() {
    return jdbc.query("SELECT * FROM analytics_dataset ORDER BY id", DATASET_MAPPER);
  }

  public Optional<Dataset> getDataset(final long id) {
    return jdbc.query("SELECT * FROM analytics_dataset WHERE id = ?", DATASET_MAPPER, id).stream()
        .findFirst();
  }

  public Dataset createDataset(
      final String name, final String dimensions, final long windowSizeMs) {
    final KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          final var ps =
              con.prepareStatement(
                  "INSERT INTO analytics_dataset (name, fact_type, dimensions, window_size_ms)"
                      + " VALUES (?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setString(1, name);
          ps.setString(2, Dataset.EXECUTION_TIME_FACT);
          ps.setString(3, dimensions);
          ps.setLong(4, windowSizeMs);
          return ps;
        },
        keys);
    return getDataset(keys.getKey().longValue()).orElseThrow();
  }

  public List<Report> listReports() {
    return jdbc.query("SELECT * FROM analytics_report ORDER BY id", REPORT_MAPPER);
  }

  public Optional<Report> getReport(final long id) {
    return jdbc.query("SELECT * FROM analytics_report WHERE id = ?", REPORT_MAPPER, id).stream()
        .findFirst();
  }

  public Report createReport(
      final String name,
      final long datasetId,
      final String vizType,
      final String bpmnProcessId,
      final String region,
      final Long fromWindow,
      final Long toWindow) {
    final KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        con -> {
          final var ps =
              con.prepareStatement(
                  "INSERT INTO analytics_report"
                      + " (name, dataset_id, viz_type, bpmn_process_id, region, from_window,"
                      + " to_window) VALUES (?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setString(1, name);
          ps.setLong(2, datasetId);
          ps.setString(3, vizType);
          ps.setString(4, bpmnProcessId);
          ps.setString(5, region);
          ps.setObject(6, fromWindow);
          ps.setObject(7, toWindow);
          return ps;
        },
        keys);
    return getReport(keys.getKey().longValue()).orElseThrow();
  }

  /**
   * Runs a report: execution time grouped by region (and process and window), with the report's
   * filters. The per-source-partition partials are merged here — {@code SUM} for additive counts
   * and total duration (so the average is exact), {@code MAX} for the slowest instance.
   */
  public List<ReportRow> runReport(final Report report) {
    final StringBuilder sql =
        new StringBuilder(
            "SELECT region, bpmn_process_id, window_start, SUM(completed_count) AS completed, "
                + "CASE WHEN SUM(completed_count) = 0 THEN 0 "
                + "ELSE SUM(total_duration_ms) * 1.0 / SUM(completed_count) END AS avg_duration, "
                + "MAX(max_duration_ms) AS max_duration "
                + "FROM proc_inst_exec_time_window");
    final List<Object> params = new ArrayList<>();
    final List<String> conditions = new ArrayList<>();
    // a report reads only its dataset's rows (each dataset is aggregated with its own window)
    conditions.add("dataset_id = ?");
    params.add(report.datasetId());
    if (report.bpmnProcessId() != null && !report.bpmnProcessId().isBlank()) {
      conditions.add("bpmn_process_id = ?");
      params.add(report.bpmnProcessId());
    }
    if (report.region() != null && !report.region().isBlank()) {
      conditions.add("region = ?");
      params.add(report.region());
    }
    if (report.fromWindow() != null) {
      conditions.add("window_start >= ?");
      params.add(report.fromWindow());
    }
    if (report.toWindow() != null) {
      conditions.add("window_start <= ?");
      params.add(report.toWindow());
    }
    sql.append(" WHERE ").append(String.join(" AND ", conditions));
    sql.append(
        " GROUP BY region, bpmn_process_id, window_start"
            + " ORDER BY region, bpmn_process_id, window_start");
    return jdbc.query(sql.toString(), REPORT_ROW_MAPPER, params.toArray());
  }

  /** The process definitions the heatmap has data for (for the filter dropdown). */
  public List<String> heatmapProcesses() {
    try {
      return jdbc.queryForList(
          "SELECT DISTINCT bpmn_process_id FROM element_execution_window ORDER BY bpmn_process_id",
          String.class);
    } catch (final DataAccessException tableNotReadyYet) {
      return List.of();
    }
  }

  /**
   * The heatmap for one process: per element, the execution count and execution-time stats, merged
   * across windows (the per-source-partition partials add for count/total, max for the slowest).
   */
  public List<HeatmapCell> elementHeatmap(final String bpmnProcessId) {
    try {
      return jdbc.query(
          "SELECT element_id, MIN(element_type) AS element_type, SUM(executed_count) AS executed, "
              + "CASE WHEN SUM(executed_count) = 0 THEN 0 "
              + "ELSE SUM(total_duration_ms) * 1.0 / SUM(executed_count) END AS avg_duration, "
              + "MAX(max_duration_ms) AS max_duration "
              + "FROM element_execution_window WHERE bpmn_process_id = ? "
              + "GROUP BY element_id ORDER BY executed DESC, element_id",
          (rs, n) ->
              new HeatmapCell(
                  rs.getString("element_id"),
                  rs.getString("element_type"),
                  rs.getLong("executed"),
                  rs.getDouble("avg_duration"),
                  rs.getLong("max_duration")),
          bpmnProcessId);
    } catch (final DataAccessException tableNotReadyYet) {
      return List.of();
    }
  }
}
