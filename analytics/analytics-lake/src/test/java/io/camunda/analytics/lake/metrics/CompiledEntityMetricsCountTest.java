/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static io.camunda.analytics.lake.metrics.EntityMetricsFixtures.userTasksRawSchema;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@code count()}'s first-class support in {@link CompiledEntityMetrics}: an unprefixed {@code cnt}
 * column on {@code _metrics}, folded into the fingerprint, rendered merge/finalize SQL — and {@link
 * CompiledEntityMetrics#hasHistogram()}, which lets a count-only (or otherwise histogram-less)
 * declaration skip {@code _hist} table creation entirely (see {@code MetricsRider}/{@code
 * PollFedRider}'s own conditional wiring).
 */
class CompiledEntityMetricsCountTest {

  @Test
  void shouldAddAnUnprefixedNonNullableCntColumnWhenCounted() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .count()
            .build();

    final TableSchema schema = compiled.metricsSchema();
    assertThat(schema.columns())
        .extracting(TableSchema.Column::name)
        .containsExactly("window_start", "process_id", "cnt");
    final TableSchema.Column cnt = schema.columns().get(2);
    assertThat(cnt.nullable()).isFalse();
  }

  @Test
  void shouldNotAddACntColumnWhenNotCounted() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    assertThat(compiled.metricsSchema().columns())
        .extracting(TableSchema.Column::name)
        .doesNotContain("cnt");
  }

  @Test
  void shouldPlaceCntRightAfterDimsAndBeforeAnyMeasureColumns() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .count()
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    assertThat(compiled.metricsSchema().columns())
        .extracting(TableSchema.Column::name)
        .containsExactly(
            "window_start",
            "process_id",
            "cnt",
            "work_time_ms_cnt",
            "work_time_ms_sum",
            "work_time_ms_min",
            "work_time_ms_max");
  }

  @Test
  void shouldChangeFingerprintWhenCountIsToggled() {
    final CompiledEntityMetrics counted =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .count()
            .build();
    final CompiledEntityMetrics uncounted =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    assertThat(counted.fingerprint()).isNotEqualTo(uncounted.fingerprint());
  }

  @Test
  void shouldRejectCountingTwice() {
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .count()
                    .count())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already");
  }

  @Test
  void shouldRejectADeclarationWithNeitherMeasuresNorCount() {
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no measures")
        .hasMessageContaining("no count()");
  }

  @Test
  void shouldReportNoHistogramForACountOnlyDeclaration() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .count()
            .build();

    assertThat(compiled.hasHistogram()).isFalse();
  }

  @Test
  void shouldReportNoHistogramWhenOnlyScalarStatsAreDeclared() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    assertThat(compiled.hasHistogram()).isFalse();
  }

  @Test
  void shouldReportHistogramWhenAnExpHistogramMeasureIsDeclared() {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    assertThat(compiled.hasHistogram()).isTrue();
  }

  /**
   * The load-bearing check, mirroring {@link EntityMetricsMergeClosureDuckDbTest}'s own approach:
   * generated merge/finalize SQL for {@code count()}, executed by a real DuckDB connection, must
   * reproduce a direct fold of the union.
   */
  @Test
  void shouldReproduceADirectCountFoldThroughGeneratedMergeAndFinalizeSql() throws Exception {
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(1))
            .count()
            .build();
    final long minuteMicros = Duration.ofMinutes(1).toNanos() / 1000;
    final long hourMicros = Duration.ofHours(1).toNanos() / 1000;

    try (Connection duckdb = DriverManager.getConnection("jdbc:duckdb:")) {
      try (Statement ddl = duckdb.createStatement()) {
        ddl.execute("CREATE TABLE metrics_a (window_start BIGINT, process_id VARCHAR, cnt BIGINT)");
        ddl.execute("CREATE TABLE metrics_b (window_start BIGINT, process_id VARCHAR, cnt BIGINT)");
        // group "proc1": 3 counted rows in A (minute 0), 2 in B (minute 1, same hour) -> 5 total
        ddl.execute("INSERT INTO metrics_a VALUES (0, 'proc1', 3)");
        ddl.execute("INSERT INTO metrics_b VALUES (" + minuteMicros + ", 'proc1', 2)");
        // group "proc2": only in A, a different hour -> stays separate after coarsening
        ddl.execute("INSERT INTO metrics_a VALUES (" + (25 * hourMicros) + ", 'proc2', 7)");
      }

      final String union = "(SELECT * FROM metrics_a UNION ALL SELECT * FROM metrics_b)";
      final String mergeSql = compiled.mergeMetricsSql(union, hourMicros);
      try (Statement create = duckdb.createStatement()) {
        create.execute("CREATE TABLE metrics_merged AS " + mergeSql);
      }
      final String finalizeSql = compiled.finalizeMetricsSql("metrics_merged");

      try (Statement query = duckdb.createStatement();
          ResultSet rs =
              query.executeQuery(
                  "SELECT * FROM (" + finalizeSql + ") WHERE process_id = 'proc1'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong("cnt")).isEqualTo(5L);
        assertThat(rs.next()).as("only one merged row for proc1's single hour").isFalse();
      }
      try (Statement query = duckdb.createStatement();
          ResultSet rs =
              query.executeQuery(
                  "SELECT * FROM (" + finalizeSql + ") WHERE process_id = 'proc2'")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getLong("cnt")).isEqualTo(7L);
      }
    }
  }
}
