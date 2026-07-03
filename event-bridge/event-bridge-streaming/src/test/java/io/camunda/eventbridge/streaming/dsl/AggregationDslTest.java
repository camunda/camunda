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
import io.camunda.eventbridge.streaming.aggregate.InMemoryRollupStore;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import org.junit.jupiter.api.Test;

/** The fluent builder produces a windowed, grouped rollup over the existing primitives. */
final class AggregationDslTest {

  private record Sale(String region, long amount, long timestamp) {}

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

  @Test
  void shouldBuildAWindowedGroupedRollup() {
    // given — group by region, windowed per second
    final InMemoryRollupStore<Windowed<String>, Long> store = new InMemoryRollupStore<>(SUM);
    final Rollup<Sale> byRegion =
        Aggregation.<Sale, String>groupBy(Sale::region)
            .windowedBy(TumblingWindows.of(1_000), Sale::timestamp)
            .aggregate(SUM)
            .into(store);

    // when
    byRegion.accept(new Sale("EU", 100, 200)); // window 0
    byRegion.accept(new Sale("EU", 50, 900)); // window 0
    byRegion.accept(new Sale("US", 70, 300)); // window 0
    byRegion.accept(new Sale("EU", 80, 1_500)); // window 1000
    byRegion.close();

    // then — one cell per (region, window)
    assertThat(store.get(new Windowed<>("EU", 0L))).contains(150L);
    assertThat(store.get(new Windowed<>("US", 0L))).contains(70L);
    assertThat(store.get(new Windowed<>("EU", 1_000L))).contains(80L);
  }
}
