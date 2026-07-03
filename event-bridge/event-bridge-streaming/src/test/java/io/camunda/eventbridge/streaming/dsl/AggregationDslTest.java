/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.InMemoryResultSink;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import org.junit.jupiter.api.Test;

/** The fluent builder produces a windowed, grouped MaterializedRollup into a ResultSink. */
final class AggregationDslTest {

  private record Sale(String region, long amount, long timestamp, long position) {}

  private static final AggregateFunction<Sale, Long, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Long createAccumulator() {
          return 0L;
        }

        @Override
        public Long add(final Sale sale, final Long acc) {
          return acc + sale.amount();
        }

        @Override
        public Long merge(final Long a, final Long b) {
          return a + b;
        }

        @Override
        public Long getResult(final Long acc) {
          return acc;
        }
      };

  private static final SourceCoordinate<Sale> COORD =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Sale sale) {
          return 0;
        }

        @Override
        public long position(final Sale sale) {
          return sale.position();
        }
      };

  @Test
  void shouldBuildAWindowedGroupedRollup() {
    // given — group by region, windowed per second, materialized into a serving sink
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final Rollup<Sale> byRegion =
        Aggregation.<Sale, String>groupBy(Sale::region)
            .windowedBy(TumblingWindows.of(1_000), Sale::timestamp)
            .aggregate(SUM)
            .into(sink, COORD);

    // when
    byRegion.accept(new Sale("EU", 100, 200, 1)); // window 0
    byRegion.accept(new Sale("EU", 50, 900, 2)); // window 0
    byRegion.accept(new Sale("US", 70, 300, 3)); // window 0
    byRegion.accept(new Sale("EU", 80, 1_500, 4)); // window 1000
    byRegion.close();

    // then — one cell per (region, window)
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(150L);
    assertThat(sink.get(new Windowed<>("US", 0L))).contains(70L);
    assertThat(sink.get(new Windowed<>("EU", 1_000L))).contains(80L);
  }
}
