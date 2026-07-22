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

import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CompiledEntityMetricsFingerprintTest {

  @Test
  void shouldBeTwelveHexCharacters() {
    // given
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(compiled.fingerprint()).matches("[0-9a-f]{12}");
  }

  @Test
  void shouldBeStableUnderDimReordering() {
    // given the same dims declared in two different orders
    final CompiledEntityMetrics inOrder =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();
    final CompiledEntityMetrics reordered =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("element_id", "process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then the fingerprint does not change — dim declaration order changes how rows sort, not
    // what a partial row means
    assertThat(inOrder.fingerprint()).isEqualTo(reordered.fingerprint());
  }

  @Test
  void shouldBeStableUnderMeasureReordering() {
    // given two measures declared in two different orders
    final CompiledEntityMetrics inOrder =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .measure("started_at", Algebras.scalarStats())
            .build();
    final CompiledEntityMetrics reordered =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("started_at", Algebras.scalarStats())
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(inOrder.fingerprint()).isEqualTo(reordered.fingerprint());
  }

  @Test
  void shouldChangeWhenHistogramScaleChanges() {
    // given the same measure at two different histogram scales
    final CompiledEntityMetrics scale3 =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.expHistogram(3))
            .build();
    final CompiledEntityMetrics scale5 =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.expHistogram(5))
            .build();

    // then: different scale means the stored partials mean something different (different bins)
    assertThat(scale3.fingerprint()).isNotEqualTo(scale5.fingerprint());
  }

  @Test
  void shouldChangeWhenWindowChanges() {
    // given the same declaration at two different window durations
    final CompiledEntityMetrics oneMinute =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(1))
            .measure("work_time_ms", Algebras.scalarStats())
            .build();
    final CompiledEntityMetrics fiveMinutes =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(5))
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(oneMinute.fingerprint()).isNotEqualTo(fiveMinutes.fingerprint());
  }

  @Test
  void shouldChangeWhenDimSetChanges() {
    // given two declarations differing only in their dim set
    final CompiledEntityMetrics oneDim =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();
    final CompiledEntityMetrics twoDims =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(oneDim.fingerprint()).isNotEqualTo(twoDims.fingerprint());
  }

  @Test
  void shouldChangeWhenAMeasureIsAdded() {
    // given two declarations differing only in an extra measure
    final CompiledEntityMetrics withoutExtra =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();
    final CompiledEntityMetrics withExtra =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .measure("started_at", Algebras.scalarStats())
            .build();

    // then
    assertThat(withoutExtra.fingerprint()).isNotEqualTo(withExtra.fingerprint());
  }
}
