/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.streaming.StreamProcessor;
import io.camunda.analytics.streaming.aggregate.InMemoryRollupStore;
import io.camunda.analytics.streaming.aggregate.Rollup;
import io.camunda.analytics.streaming.dsl.Aggregation;
import io.camunda.analytics.streaming.fold.Projector;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Worked example: from one execution-time fact, two pre-aggregated hourly aggregations — grouped by
 * region and (across regions) by definition — built with the fluent DSL. Adding an aggregation is
 * one more {@code groupBy(...).windowedBy(...).aggregate(...).into(...)} rollup registered with the
 * projector. (Wiring — turning consumed records into these facts — is separate; here a
 * fact-deriving projector stands in.)
 */
final class ExecutionTimeRollupExampleTest {

  private static final long HOUR = 3_600_000L;

  @Test
  void executionTimeByRegionAndByDefinitionPreAggregatedHourly() {
    final TumblingWindows hourly = TumblingWindows.of(HOUR);
    final ExecutionTimeAggregateFunction<ProcessInstanceExecutionTimeFact> metric =
        new ExecutionTimeAggregateFunction<>(ProcessInstanceExecutionTimeFact::durationMs);

    // two serving stores, two groupings of the same fact, keyed by Windowed<base key>
    final InMemoryRollupStore<Windowed<String>, ExecutionTimeAccumulator> byRegion =
        new InMemoryRollupStore<>(metric);
    final InMemoryRollupStore<Windowed<Long>, ExecutionTimeAccumulator> byDefinition =
        new InMemoryRollupStore<>(metric);

    final Rollup<ProcessInstanceExecutionTimeFact> regionRollup =
        Aggregation.<ProcessInstanceExecutionTimeFact, String>groupBy(
                ExecutionTimeRollupExampleTest::region)
            .windowedBy(hourly, ProcessInstanceExecutionTimeFact::endTime)
            .aggregate(metric)
            .into(byRegion);

    final Rollup<ProcessInstanceExecutionTimeFact> definitionRollup =
        Aggregation.<ProcessInstanceExecutionTimeFact, Long>groupBy(
                ProcessInstanceExecutionTimeFact::processDefinitionKey)
            .windowedBy(hourly, ProcessInstanceExecutionTimeFact::endTime)
            .aggregate(metric)
            .into(byDefinition);

    // in production this is the process-instance fold, Projector<ZeebeRecord, fact>; here it just
    // forwards the facts a completed instance would yield
    final Projector<ProcessInstanceExecutionTimeFact, ProcessInstanceExecutionTimeFact> derive =
        (fact, out) -> out.collect(fact);

    final StreamProcessor<ProcessInstanceExecutionTimeFact> processor =
        new StreamProcessor<ProcessInstanceExecutionTimeFact>()
            .register(derive, List.of(regionRollup, definitionRollup));

    processor.init();
    List.of(
            completion("EU", 600L, 500L), // hour 0
            completion("EU", 1_200L, 300L), // hour 0
            completion("US", 3_000L, 700L), // hour 0
            completion("EU", HOUR + 900L, 800L)) // hour 1
        .forEach(processor::process);
    processor.close(); // final flush

    // by region (region is part of the key)
    final ExecutionTimeResult euHour0 =
        metric.getResult(byRegion.get(new Windowed<>("EU", 0L)).orElseThrow());
    assertThat(euHour0.count()).isEqualTo(2L);
    assertThat(euHour0.averageMs()).isEqualTo(400.0); // (500 + 300) / 2
    assertThat(euHour0.maxMs()).isEqualTo(500L);
    assertThat(metric.getResult(byRegion.get(new Windowed<>("US", 0L)).orElseThrow()).averageMs())
        .isEqualTo(700.0);

    // by definition (regions rolled up together)
    final ExecutionTimeResult def77Hour0 =
        metric.getResult(byDefinition.get(new Windowed<>(77L, 0L)).orElseThrow());
    assertThat(def77Hour0.count()).isEqualTo(3L); // EU 500, EU 300, US 700
    assertThat(def77Hour0.averageMs()).isEqualTo(500.0); // 1500 / 3
    assertThat(def77Hour0.maxMs()).isEqualTo(700L);
  }

  private static String region(final ProcessInstanceExecutionTimeFact fact) {
    return fact.variables().getOrDefault("region", "<none>");
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
