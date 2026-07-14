/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.webapp.ServingTestSupport.Fixture;
import io.camunda.analytics.webapp.model.Dataset;
import io.camunda.analytics.webapp.model.HeatmapCell;
import io.camunda.analytics.webapp.model.Report;
import io.camunda.analytics.webapp.model.ReportRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

final class AnalyticsRepositoryTest {

  private JdbcTemplate jdbc;
  private Fixture fixture;
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
            + " region VARCHAR(255), from_window BIGINT, to_window BIGINT)");
    fixture = ServingTestSupport.create();
    repository = new AnalyticsRepository(jdbc, fixture.executor(), fixture.catalog());
  }

  @AfterEach
  void tearDown() {
    fixture.datasetStore().close();
    fixture.metadataStore().close();
  }

  @Test
  void shouldCreateAndListDatasetsAndReports() {
    // when
    final Dataset dataset = repository.createDataset("Completions", "definition", 3_600_000L);
    final Report report =
        repository.createReport("Hourly", dataset.id(), "table", null, null, null, null);

    // then
    assertThat(dataset.id()).isPositive();
    assertThat(dataset.factType()).isEqualTo(Dataset.EXECUTION_TIME_FACT);
    assertThat(repository.listDatasets()).extracting(Dataset::name).containsExactly("Completions");
    assertThat(report.datasetId()).isEqualTo(dataset.id());
    assertThat(repository.listReports()).extracting(Report::name).containsExactly("Hourly");
  }

  @Test
  void shouldRunReportOverProcessInstanceLifecycle() {
    // given — three completed instances of a process folded into the process-instances cube
    // (per-transition counts + duration primitives)
    ServingTestSupport.seed(
        fixture,
        "process-instances",
        "completed",
        lifecycle(3, 1500L, 900L, 600L),
        "order-process");
    final Dataset dataset = repository.createDataset("Completions", "definition", 60_000L);
    final Report report =
        repository.createReport("All", dataset.id(), "table", null, null, null, null);

    // when
    final List<ReportRow> rows = repository.runReport(report);

    // then — one row for the process/window with the completed count and derived average
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.bpmnProcessId()).isEqualTo("order-process");
              assertThat(row.completedCount()).isEqualTo(3L);
              assertThat(row.averageDurationMs()).isEqualTo(1000.0); // (1500 + 900 + 600) / 3
              assertThat(row.maxDurationMs()).isEqualTo(1500L);
            });
  }

  @Test
  void shouldReadElementHeatmapThroughExecutor() {
    // given — two executions of one element folded into the element-duration cube
    ServingTestSupport.seed(
        fixture,
        "element-duration",
        "duration",
        elementDurations(200L, 400L),
        "order-process",
        "Task_Validate");

    // when
    final List<HeatmapCell> heatmap = repository.elementHeatmap("order-process");

    // then
    assertThat(heatmap)
        .singleElement()
        .satisfies(
            cell -> {
              assertThat(cell.elementId()).isEqualTo("Task_Validate");
              assertThat(cell.executedCount()).isEqualTo(2L);
              assertThat(cell.averageDurationMs()).isEqualTo(300.0);
              assertThat(cell.maxDurationMs()).isEqualTo(400L);
            });
    assertThat(repository.heatmapProcesses()).containsExactly("order-process");
  }

  private static List<Fact> lifecycle(final int completed, final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (int i = 0; i < completed; i++) {
      facts.add(Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.ACTIVATED).build());
    }
    for (final long duration : durationsMs) {
      facts.add(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .build());
    }
    return facts;
  }

  private static List<Fact> elementDurations(final long... durationsMs) {
    final List<Fact> facts = new ArrayList<>();
    for (final long duration : durationsMs) {
      facts.add(
          Fact.builder(FactType.ELEMENT)
              .transition(Transition.COMPLETED)
              .field("durationMs", duration)
              .build());
    }
    return facts;
  }
}
