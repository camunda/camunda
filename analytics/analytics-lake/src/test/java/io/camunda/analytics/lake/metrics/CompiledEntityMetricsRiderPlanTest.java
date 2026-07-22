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

class CompiledEntityMetricsRiderPlanTest {

  @Test
  void shouldResolveDimAndMeasureColumnIndexesFromRawSchema() {
    // given a declaration over (process_id, element_id) dims and a work_time_ms measure
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .window(Duration.ofMinutes(1))
            .measure("work_time_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // then dims resolve to raw column indexes 0 and 1, measure to raw column index 3
    final RiderPlan plan = compiled.riderPlan();
    assertThat(plan.dimColumnIndexes()).containsExactly(0, 1);
    assertThat(plan.measureColumnIndexes()).containsExactly(3);
    assertThat(plan.windowMicros()).isEqualTo(60_000_000L);
  }

  @Test
  void shouldReportNoWindowAsZero() {
    // given a declaration with no .window(...) call
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(compiled.riderPlan().windowMicros()).isZero();
  }

  @Test
  void shouldComputeRunPrefixLengthWhenDimsMatchRawSortOrder() {
    // given dims (process_id, element_id) exactly matching the raw schema's own sort key order
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("process_id", "element_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then both dims form a matching prefix of the raw schema's sort key
    assertThat(compiled.riderPlan().runPrefixLength()).isEqualTo(2);
  }

  @Test
  void shouldComputeRunPrefixLengthOfZeroWhenDimsDoNotMatchRawSortOrderPrefix() {
    // given a single dim (element_id) which is the raw schema's SECOND sort key column, not its
    // first — so it cannot form a matching prefix starting at position 0
    // when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("user_tasks", userTasksRawSchema())
            .dims("element_id")
            .measure("work_time_ms", Algebras.scalarStats())
            .build();

    // then
    assertThat(compiled.riderPlan().runPrefixLength()).isZero();
  }
}
