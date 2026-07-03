/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.streaming.ProjectionStage;
import io.camunda.eventbridge.streaming.StreamProcessor;
import io.camunda.eventbridge.streaming.aggregate.InMemoryResultSink;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.dsl.Aggregation;
import io.camunda.eventbridge.streaming.fold.Projector;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
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

  // dedup coordinate: the fact's origin (source partition + position on the source log)
  private static final SourceCoordinate<ProcessInstanceExecutionTimeFact> COORD =
      new SourceCoordinate<>() {
        @Override
        public int partition(final ProcessInstanceExecutionTimeFact fact) {
          return fact.sourcePartitionId();
        }

        @Override
        public long position(final ProcessInstanceExecutionTimeFact fact) {
          return fact.sourcePosition();
        }
      };

  @Test
  void executionTimeByRegionAndByDefinitionPreAggregatedHourly() {
    final TumblingWindows hourly = TumblingWindows.of(HOUR);
    final ExecutionTimeAggregateFunction<ProcessInstanceExecutionTimeFact> metric =
        new ExecutionTimeAggregateFunction<>(ProcessInstanceExecutionTimeFact::durationMs);

    // two serving sinks, two groupings of the same fact, keyed by Windowed<base key>
    final InMemoryResultSink<Windowed<String>, ExecutionTimeAccumulator> byRegion =
        new InMemoryResultSink<>();
    final InMemoryResultSink<Windowed<Long>, ExecutionTimeAccumulator> byDefinition =
        new InMemoryResultSink<>();

    final Rollup<ProcessInstanceExecutionTimeFact> regionRollup =
        Aggregation.<ProcessInstanceExecutionTimeFact, String>groupBy(
                ExecutionTimeRollupExampleTest::region)
            .windowedBy(hourly, ProcessInstanceExecutionTimeFact::endTime)
            .aggregate(metric)
            .into(byRegion, COORD);

    final Rollup<ProcessInstanceExecutionTimeFact> definitionRollup =
        Aggregation.<ProcessInstanceExecutionTimeFact, Long>groupBy(
                ProcessInstanceExecutionTimeFact::processDefinitionKey)
            .windowedBy(hourly, ProcessInstanceExecutionTimeFact::endTime)
            .aggregate(metric)
            .into(byDefinition, COORD);

    // in production this is the process-instance fold, Projector<ZeebeRecord, fact>; here it just
    // forwards the facts a completed instance would yield
    final Projector<ProcessInstanceExecutionTimeFact, ProcessInstanceExecutionTimeFact> derive =
        (fact, out) -> out.collect(fact);

    final StreamProcessor<ProcessInstanceExecutionTimeFact> processor =
        new StreamProcessor<ProcessInstanceExecutionTimeFact>()
            .add(
                new ProjectionStage<
                    ProcessInstanceExecutionTimeFact, ProcessInstanceExecutionTimeFact>(
                    derive, List.of(regionRollup, definitionRollup)));

    processor.init();
    List.of(
            completion("EU", 600L, 500L, 1L), // hour 0
            completion("EU", 1_200L, 300L, 2L), // hour 0
            completion("US", 3_000L, 700L, 3L), // hour 0
            completion("EU", HOUR + 900L, 800L, 4L)) // hour 1
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
      final String region, final long endTime, final long durationMs, final long position) {
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
        false,
        1,
        position,
        Map.of("region", region));
  }
}
