/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.metric.ExecutionTimeSummaryResult;
import io.camunda.analytics.metric.LifecycleSummaryResult;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.ReportQuery;
import io.camunda.analytics.webapp.model.Dataset;
import io.camunda.analytics.webapp.model.HeatmapCell;
import io.camunda.analytics.webapp.model.Report;
import io.camunda.analytics.webapp.model.ReportRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Stores dataset/report <em>definitions</em> in H2 (its own tables, unrelated to the serving store)
 * and runs a report/heatmap by querying the neutral serving executor. A report reads the
 * process-instances lifecycle cube grouped by process and window; the heatmap reads the
 * element-duration cube grouped by element.
 */
@Repository
public class AnalyticsRepository {

  private static final long ONE_HOUR_MS = 3_600_000L;
  private static final long MINUTE_MS = 60_000L;

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

  private final JdbcTemplate jdbc;
  private final DatasetQueryExecutor executor;
  private final DatasetCatalog catalog;

  public AnalyticsRepository(
      final JdbcTemplate jdbc, final DatasetQueryExecutor executor, final DatasetCatalog catalog) {
    this.jdbc = jdbc;
    this.executor = executor;
    this.catalog = catalog;
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
   * Runs a report: process-instance lifecycle per process and 1-minute window, optionally filtered
   * to the report's process. The region filter is not modeled by the lifecycle cube (no region
   * dimension), so it is ignored and the region column reads empty.
   */
  public List<ReportRow> runReport(final Report report) {
    final CompiledDataset dataset = catalog.require("process-instances");
    final long fromMs = report.fromWindow() == null ? 0L : report.fromWindow();
    final long toMs =
        report.toWindow() == null ? System.currentTimeMillis() + ONE_HOUR_MS : report.toWindow();
    final List<FilterPredicate> filters = new ArrayList<>();
    if (report.bpmnProcessId() != null && !report.bpmnProcessId().isBlank()) {
      filters.add(FilterPredicate.equals("bpmnProcessId", report.bpmnProcessId()));
    }
    final ReportQuery query =
        new ReportQuery(
            List.of("bpmnProcessId"), fromMs, toMs, MINUTE_MS, filters, List.of("lifecycle"));
    final List<ReportRow> rows = new ArrayList<>();
    for (final var row : executor.execute(query, dataset).rows()) {
      final LifecycleSummaryResult lifecycle =
          (LifecycleSummaryResult) row.measures().get("lifecycle");
      final Object process = row.dimensions().get("bpmnProcessId");
      rows.add(
          new ReportRow(
              "",
              process == null ? "" : process.toString(),
              row.windowStart(),
              lifecycle.completed(),
              lifecycle.duration().averageMs(),
              lifecycle.duration().maxMs()));
    }
    rows.sort(
        Comparator.comparing(ReportRow::bpmnProcessId).thenComparingLong(ReportRow::windowStart));
    return rows;
  }

  /** The process definitions the element-duration cube has data for (for the filter dropdown). */
  public List<String> heatmapProcesses() {
    final CompiledDataset dataset = catalog.require("element-duration");
    final TreeSet<String> ids = new TreeSet<>();
    for (final var row :
        executor.execute(total(dataset, "bpmnProcessId", "duration"), dataset).rows()) {
      final Object id = row.dimensions().get("bpmnProcessId");
      if (id != null) {
        ids.add(id.toString());
      }
    }
    return new ArrayList<>(ids);
  }

  /** The element heatmap for one process: per element, execution count + duration stats. */
  public List<HeatmapCell> elementHeatmap(final String bpmnProcessId) {
    final CompiledDataset dataset = catalog.require("element-duration");
    final long toMs = System.currentTimeMillis() + ONE_HOUR_MS;
    final ReportQuery query =
        new ReportQuery(
            List.of("elementId"),
            0L,
            toMs,
            toMs,
            List.of(FilterPredicate.equals("bpmnProcessId", bpmnProcessId)),
            List.of("duration"));
    final List<HeatmapCell> cells = new ArrayList<>();
    for (final var row : executor.execute(query, dataset).rows()) {
      final ExecutionTimeSummaryResult d =
          (ExecutionTimeSummaryResult) row.measures().get("duration");
      final Object elementId = row.dimensions().get("elementId");
      cells.add(
          new HeatmapCell(
              elementId == null ? "" : elementId.toString(),
              "",
              d.count(),
              d.averageMs(),
              d.maxMs()));
    }
    cells.sort(Comparator.comparingLong(HeatmapCell::executedCount).reversed());
    return cells;
  }

  private static ReportQuery total(
      final CompiledDataset dataset, final String groupBy, final String meter) {
    final long toMs = System.currentTimeMillis() + ONE_HOUR_MS;
    return new ReportQuery(List.of(groupBy), 0L, toMs, toMs, List.of(), List.of(meter));
  }
}
