/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.streaming.InMemoryRollupStore;
import io.camunda.analytics.streaming.PreAggregatingRollup;
import io.camunda.analytics.streaming.Projector;
import io.camunda.analytics.streaming.Rollup;
import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Worked example: from one execution-time fact, two pre-aggregated hourly aggregations — grouped by
 * region and (across regions) by definition — assembled from the {@code analytics-streaming}
 * framework. Adding a second aggregation is registering another rollup with its own key selector.
 * (Wiring — turning consumed records into these facts via the process-instance projector — is
 * separate; here a fact-deriving projector stands in.)
 */
final class ExecutionTimeRollupExampleTest {

  private static final long HOUR = 3_600_000L;

  @Test
  void executionTimeByRegionAndByDefinitionPreAggregatedHourly() {
    // --- shared wiring -----------------------------------------------------------------------
    final TumblingWindows hourly = TumblingWindows.of(HOUR);
    final ExecutionTimeAggregateFunction metric = new ExecutionTimeAggregateFunction();

    // two serving stores, two groupings of the same fact
    final InMemoryRollupStore<RegionWindowKey, ExecutionTimeAccumulator> byRegion =
        new InMemoryRollupStore<>(metric);
    final InMemoryRollupStore<DefinitionWindowKey, ExecutionTimeAccumulator> byDefinition =
        new InMemoryRollupStore<>(metric);

    final Rollup<ProcessInstanceExecutionTimeFact> regionRollup =
        new PreAggregatingRollup<>(metric, new RegionWindowKeySelector(hourly), byRegion, 1_000);
    final Rollup<ProcessInstanceExecutionTimeFact> definitionRollup =
        new PreAggregatingRollup<>(
            metric, new DefinitionWindowKeySelector(hourly), byDefinition, 1_000);

    // in production this is the process-instance fold, Projector<ZeebeRecord, fact>; here it just
    // forwards the facts a completed instance would yield
    final Projector<ProcessInstanceExecutionTimeFact, ProcessInstanceExecutionTimeFact> derive =
        (fact, out) -> out.collect(fact);

    final StreamProcessor<ProcessInstanceExecutionTimeFact> processor =
        new StreamProcessor<ProcessInstanceExecutionTimeFact>()
            .register(derive, List.of(regionRollup, definitionRollup));

    // --- run ---------------------------------------------------------------------------------
    processor.init();
    List.of(
            completion("EU", 600L, 500L), // hour 0
            completion("EU", 1_200L, 300L), // hour 0
            completion("US", 3_000L, 700L), // hour 0
            completion("EU", HOUR + 900L, 800L)) // hour 1
        .forEach(processor::process);
    processor.close(); // final flush

    // --- by region (region is part of the key) ----------------------------------------------
    final ExecutionTimeResult euHour0 =
        metric.getResult(byRegion.get(region("EU", 0L)).orElseThrow());
    assertThat(euHour0.count()).isEqualTo(2L);
    assertThat(euHour0.averageMs()).isEqualTo(400.0); // (500 + 300) / 2
    assertThat(euHour0.maxMs()).isEqualTo(500L);
    assertThat(metric.getResult(byRegion.get(region("US", 0L)).orElseThrow()).averageMs())
        .isEqualTo(700.0);

    // --- by definition (regions rolled up together) ------------------------------------------
    final ExecutionTimeResult def77Hour0 =
        metric.getResult(byDefinition.get(definition(0L)).orElseThrow());
    assertThat(def77Hour0.count()).isEqualTo(3L); // EU 500, EU 300, US 700
    assertThat(def77Hour0.averageMs()).isEqualTo(500.0); // 1500 / 3
    assertThat(def77Hour0.maxMs()).isEqualTo(700L);
  }

  private static RegionWindowKey region(final String region, final long windowStart) {
    return new RegionWindowKey(region, 77L, 1, "<default>", windowStart);
  }

  private static DefinitionWindowKey definition(final long windowStart) {
    return new DefinitionWindowKey(77L, 1, "<default>", windowStart);
  }

  private static ProcessInstanceExecutionTimeFact completion(
      final String region, final long endTime, final long durationMs) {
    return new ProcessInstanceExecutionTimeFact(
        endTime,
        77L,
        "order",
        1,
        "<default>",
        endTime - durationMs,
        endTime,
        durationMs,
        true,
        1,
        endTime,
        Map.of("region", region));
  }
}
