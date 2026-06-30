/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.webapp.model.Dataset;
import io.camunda.analytics.webapp.model.Report;
import io.camunda.analytics.webapp.model.ReportRow;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

final class AnalyticsRepositoryTest {

  private JdbcTemplate jdbc;
  private AnalyticsRepository repository;

  @BeforeEach
  void setUp() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:webapp-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(ds);
    jdbc.execute(
        "CREATE TABLE analytics_dataset (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255),"
            + " fact_type VARCHAR(128), dimensions VARCHAR(512), window_size_ms BIGINT)");
    jdbc.execute(
        "CREATE TABLE analytics_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255),"
            + " dataset_id BIGINT, viz_type VARCHAR(64), bpmn_process_id VARCHAR(255),"
            + " from_window BIGINT, to_window BIGINT)");
    jdbc.execute(
        "CREATE TABLE proc_inst_exec_time_window (process_definition_key BIGINT, bpmn_process_id"
            + " VARCHAR(255), version INT, tenant_id VARCHAR(255), window_start BIGINT,"
            + " window_size_ms BIGINT, completed_count BIGINT, total_duration_ms BIGINT,"
            + " min_duration_ms BIGINT, max_duration_ms BIGINT)");
    seed(1L, "order", 0L, 3, 1500);
    seed(1L, "order", 3_600_000L, 1, 800);
    seed(2L, "invoice", 0L, 1, 3950);
    repository = new AnalyticsRepository(jdbc);
  }

  @Test
  void shouldCreateAndListDatasetsAndReports() {
    // when
    final Dataset dataset = repository.createDataset("Completions", "definition", 3_600_000L);
    final Report report =
        repository.createReport("Hourly", dataset.id(), "table", null, null, null);

    // then
    assertThat(dataset.id()).isPositive();
    assertThat(dataset.factType()).isEqualTo(Dataset.EXECUTION_TIME_FACT);
    assertThat(repository.listDatasets()).extracting(Dataset::name).containsExactly("Completions");
    assertThat(report.datasetId()).isEqualTo(dataset.id());
    assertThat(repository.listReports()).extracting(Report::name).containsExactly("Hourly");
  }

  @Test
  void shouldRunReportAggregatingPerProcessAndWindow() {
    // given
    final Dataset dataset = repository.createDataset("Completions", "definition", 3_600_000L);
    final Report report = repository.createReport("All", dataset.id(), "table", null, null, null);

    // when
    final var rows = repository.runReport(report);

    // then — one row per (process, window), counts summed
    assertThat(rows)
        .extracting(ReportRow::bpmnProcessId, ReportRow::windowStart, ReportRow::completedCount)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("order", 0L, 3L),
            org.assertj.core.groups.Tuple.tuple("order", 3_600_000L, 1L),
            org.assertj.core.groups.Tuple.tuple("invoice", 0L, 1L));
    final ReportRow orderHour0 =
        rows.stream()
            .filter(r -> r.bpmnProcessId().equals("order") && r.windowStart() == 0L)
            .findFirst()
            .orElseThrow();
    assertThat(orderHour0.averageDurationMs()).isEqualTo(500.0); // 1500 / 3
  }

  @Test
  void shouldApplyProcessFilter() {
    // given
    final Dataset dataset = repository.createDataset("Completions", "definition", 3_600_000L);
    final Report report =
        repository.createReport("Order only", dataset.id(), "table", "order", null, null);

    // when
    final var rows = repository.runReport(report);

    // then
    assertThat(rows).extracting(ReportRow::bpmnProcessId).containsOnly("order");
    assertThat(rows).hasSize(2);
  }

  private void seed(
      final long defKey,
      final String bpmnProcessId,
      final long windowStart,
      final long count,
      final long totalDuration) {
    jdbc.update(
        "INSERT INTO proc_inst_exec_time_window VALUES (?, ?, 1, '<default>', ?, 3600000, ?, ?, 0, 0)",
        defKey,
        bpmnProcessId,
        windowStart,
        count,
        totalDuration);
  }
}
