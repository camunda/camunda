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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CompiledEntityMetricsPartialsSchemaTest {

  private static CompiledEntityMetrics compiled() {
    return EntityMetrics.declare("user_tasks", userTasksRawSchema())
        .dims("process_id", "element_id")
        .window(Duration.ofMinutes(1))
        .measure("work_time_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
        .build();
  }

  @Test
  void shouldGenerateWideMetricsSchemaWithWindowStartFirstThenDimsThenMeasureColumns() {
    // given a compiled entity with one scalar-stats measure
    // when
    final TableSchema schema = compiled().metricsSchema();

    // then the table is named <entity>_metrics and columns follow window_start, dims, measure*
    assertThat(schema.table()).isEqualTo("user_tasks_metrics");
    assertThat(schema.columns())
        .extracting(TableSchema.Column::name)
        .containsExactly(
            "window_start",
            "process_id",
            "element_id",
            "work_time_ms_cnt",
            "work_time_ms_sum",
            "work_time_ms_min",
            "work_time_ms_max");
  }

  @Test
  void shouldAssignSequentialFieldIdsStartingAtOne() {
    // given
    final TableSchema schema = compiled().metricsSchema();

    // then
    assertThat(schema.columns())
        .extracting(TableSchema.Column::icebergFieldId)
        .containsExactly(1, 2, 3, 4, 5, 6, 7);
  }

  @Test
  void shouldSortByDimsThenWindowStart() {
    // given
    final TableSchema schema = compiled().metricsSchema();

    // then sortOrder is dims first (0, 1), window_start last (2); measure columns are not sort keys
    assertThat(schema.sortKeyColumns())
        .containsExactly(1, 2, 0); // process_id, element_id, window_start
    assertThat(schema.columns().get(0).sortOrder()).isEqualTo(2); // window_start
    assertThat(schema.columns().get(1).sortOrder()).isEqualTo(0); // process_id
    assertThat(schema.columns().get(2).sortOrder()).isEqualTo(1); // element_id
  }

  @Test
  void shouldMarkWindowStartAsTheFamilyDaySourceWithTimestamptzLogicalType() {
    // given
    final TableSchema schema = compiled().metricsSchema();

    // then
    final TableSchema.Column windowStart = schema.columns().get(0);
    assertThat(windowStart.familyDaySource()).isTrue();
    assertThat(windowStart.logicalType()).isEqualTo(TableSchema.LogicalType.TIMESTAMPTZ);
    assertThat(schema.familyDayColumn()).isZero();
  }

  @Test
  void shouldMarkCntAndSumNonNullableAndMinMaxNullable() {
    // given
    final TableSchema schema = compiled().metricsSchema();

    // then (see ScalarStatsAlgebra's javadoc for why min/max are nullable)
    assertThat(schema.columns())
        .filteredOn(c -> c.name().startsWith("work_time_ms"))
        .extracting(TableSchema.Column::name, TableSchema.Column::nullable)
        .containsExactly(
            tuple("work_time_ms_cnt", false),
            tuple("work_time_ms_sum", false),
            tuple("work_time_ms_min", true),
            tuple("work_time_ms_max", true));
  }

  @Test
  void shouldPreserveRawDimColumnTypes() {
    // given a dim that is STRING_DICT in the raw schema
    final TableSchema schema = compiled().metricsSchema();

    // then
    assertThat(schema.columns().get(1).type()).isEqualTo(ColumnType.STRING_DICT);
    assertThat(schema.columns().get(2).type()).isEqualTo(ColumnType.STRING_DICT);
  }

  @Test
  void shouldGenerateTallHistSchemaWithMeasureAndSchemeColumns() {
    // given
    final TableSchema schema = compiled().histSchema();

    // then
    assertThat(schema.table()).isEqualTo("user_tasks_hist");
    assertThat(schema.columns())
        .extracting(TableSchema.Column::name)
        .containsExactly(
            "window_start",
            "process_id",
            "element_id",
            "measure",
            "scheme",
            "bin_lo",
            "bin_hi",
            "cnt");
  }

  @Test
  void shouldSortHistByDimsThenWindowStartThenMeasureThenBinLo() {
    // given
    final TableSchema schema = compiled().histSchema();

    // then: process_id(0), element_id(1), window_start(2), measure(3), bin_lo(4)
    assertThat(schema.sortKeyColumns()).containsExactly(1, 2, 0, 3, 5);
  }

  @Test
  void shouldGenerateHistSchemaEvenWithoutAnyHistogramMeasure() {
    // given an entity with only a scalar-stats measure (no histogram)
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // when / then: the generic hist schema is still produced (see CompiledEntityMetrics#histSchema)
    final TableSchema hist = compiled.histSchema();
    assertThat(hist.columns())
        .extracting(TableSchema.Column::name)
        .contains("bin_lo", "bin_hi", "cnt");
  }
}
