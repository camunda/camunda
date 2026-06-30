/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.streaming.Collector;
import io.camunda.analytics.streaming.PreAggregator;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Worked example: execution time of process instances grouped by region, pre-aggregated into hourly
 * windows — assembled purely from the {@code analytics-streaming} seams (no DB). This is exactly
 * how a consumer wires the library; the only domain pieces are the metric, the key, and the facts.
 */
final class ExecutionTimeByRegionExampleTest {

  private static final long HOUR = 3_600_000L;

  @Test
  void executionTimeByRegionPreAggregatedHourly() {
    // --- wiring: window, metric, key selector, combiner --------------------------------------
    final TumblingWindows hourly = TumblingWindows.of(HOUR);
    final ExecutionTimeAggregateFunction metric = new ExecutionTimeAggregateFunction();
    final RegionWindowKeySelector keyOf = new RegionWindowKeySelector(hourly);
    final PreAggregator<ProcessInstanceExecutionTimeFact, RegionWindowKey, ExecutionTimeAccumulator>
        combiner = new PreAggregator<>(metric);

    // the collector a stage-1 fold (Projector) emits derived facts into: each fact is folded into
    // its region/window cell — no per-fact serving-store write
    final Collector<ProcessInstanceExecutionTimeFact> out =
        fact -> combiner.add(keyOf.getKey(fact), fact);

    // --- facts the fold would emit (region carried as a variable) ----------------------------
    final List<ProcessInstanceExecutionTimeFact> facts =
        List.of(
            completion("EU", 600L, 500L, 1L), // hour 0
            completion("EU", 1_200L, 300L, 2L), // hour 0
            completion("US", 3_000L, 700L, 3L), // hour 0
            completion("EU", HOUR + 900L, 800L, 4L)); // hour 1
    facts.forEach(out::collect);

    // --- flush: drain the buffered partials and read the results -----------------------------
    final Map<RegionWindowKey, ExecutionTimeAccumulator> partials = combiner.drain();

    final ExecutionTimeResult euHour0 = metric.getResult(partials.get(key("EU", 0L)));
    assertThat(euHour0.count()).isEqualTo(2L);
    assertThat(euHour0.averageMs()).isEqualTo(400.0); // (500 + 300) / 2
    assertThat(euHour0.maxMs()).isEqualTo(500L);

    final ExecutionTimeResult usHour0 = metric.getResult(partials.get(key("US", 0L)));
    assertThat(usHour0.count()).isEqualTo(1L);
    assertThat(usHour0.averageMs()).isEqualTo(700.0);

    final ExecutionTimeResult euHour1 = metric.getResult(partials.get(key("EU", HOUR)));
    assertThat(euHour1.count()).isEqualTo(1L);
    assertThat(euHour1.averageMs()).isEqualTo(800.0);

    // three region/window cells from four facts — EU hour 0 collapsed two facts into one partial
    assertThat(partials).hasSize(3);
  }

  private static RegionWindowKey key(final String region, final long windowStart) {
    return new RegionWindowKey(region, 77L, 1, "<default>", windowStart);
  }

  private static ProcessInstanceExecutionTimeFact completion(
      final String region, final long endTime, final long durationMs, final long sourcePosition) {
    return new ProcessInstanceExecutionTimeFact(
        endTime, // processInstanceKey (unique enough per fact here)
        77L,
        "order",
        1,
        "<default>",
        endTime - durationMs,
        endTime,
        durationMs,
        true,
        1,
        sourcePosition,
        Map.of("region", region));
  }
}
