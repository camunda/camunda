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
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Every declaration validation rule {@link EntityMetrics.Builder#build()} enforces. */
class EntityMetricsValidationTest {

  @Test
  void shouldRejectUnknownDim() {
    // given a dim name that does not exist in the raw schema
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("no_such_column")
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no_such_column")
        .hasMessageContaining("not found");
  }

  @Test
  void shouldRejectDimOfWrongType() {
    // given a dim pointing at a LONG raw column (must be STRING_DICT or INT)
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("work_time_ms")
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("work_time_ms")
        .hasMessageContaining("STRING_DICT or INT");
  }

  @Test
  void shouldAcceptIntDim() {
    // given a dim pointing at an INT raw column
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("retry_count")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(compiled.riderPlan().dimColumnIndexes()).containsExactly(4);
  }

  @Test
  void shouldRejectUnknownMeasure() {
    // given a measure name that does not exist in the raw schema
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .measure("no_such_measure", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no_such_measure")
        .hasMessageContaining("not found");
  }

  @Test
  void shouldRejectMeasureOfWrongType() {
    // given a measure pointing at a STRING_DICT raw column (must be LONG)
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .measure("element_id", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("element_id")
        .hasMessageContaining("must be LONG");
  }

  @Test
  void shouldRejectWindowNotEvenlyDividingAnHour() {
    // given a 7-minute window (60 minutes is not evenly divisible by 7)
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .window(Duration.ofMinutes(7))
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("evenly divide 1 hour");
  }

  @Test
  void shouldRejectWindowShorterThanOneSecond() {
    // given a 500ms window
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .window(Duration.ofMillis(500))
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shorter than the minimum of 1 second");
  }

  @Test
  void shouldAcceptWindowEvenlyDividingAnHour() {
    // given a 15-minute window (evenly divides 60 minutes)
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(15))
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(compiled.riderPlan().windowMicros())
        .isEqualTo(Duration.ofMinutes(15).toNanos() / 1000);
  }

  @Test
  void shouldRejectNoDims() {
    // given no dims declared
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no dims");
  }

  @Test
  void shouldRejectNoMeasures() {
    // given no measures declared
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no measures");
  }

  @Test
  void shouldRejectBlankEntityName() {
    // given a blank entity name
    // when / then
    final TableSchema schema = userTasksRawSchema();
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("  ", schema)
                    .dims("process_id")
                    .measure("work_time_ms", Algebras.scalarStats())
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void shouldRejectMeasureWithNoAlgebras() {
    // given a measure declared with zero algebras
    // when / then
    assertThatThrownBy(
            () ->
                EntityMetrics.declare("user_tasks", userTasksRawSchema())
                    .dims("process_id")
                    .measure("work_time_ms")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no algebras");
  }
}
