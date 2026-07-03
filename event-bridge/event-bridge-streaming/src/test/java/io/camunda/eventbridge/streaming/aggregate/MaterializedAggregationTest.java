/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import org.junit.jupiter.api.Test;

final class MaterializedAggregationTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;

  private record Sale(String region, long amount, long timestamp, int partition, long position) {}

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
          return sale.partition();
        }

        @Override
        public long position(final Sale sale) {
          return sale.position();
        }
      };

  private InMemoryResultSink<Windowed<String>, Long> sink;
  private MaterializedAggregation<Sale, String, Long> rollup;

  private MaterializedAggregation<Sale, String, Long> newRollup() {
    sink = new InMemoryResultSink<>();
    return new MaterializedAggregation<>(
        SUM,
        Sale::region,
        Sale::timestamp,
        COORD,
        TumblingWindows.ofSizeAndGrace(HOUR, LATENESS),
        sink);
  }

  @Test
  void shouldUpsertTheFullCurrentValueNotADelta() {
    // given
    rollup = newRollup();

    // when — two sales, flushed; then a third, flushed again
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.accept(new Sale("EU", 50, 2_000L, 1, 2L));
    rollup.flush();
    rollup.accept(new Sale("EU", 30, 3_000L, 1, 3L));
    rollup.flush();

    // then — the sink holds the full running value, overwritten each time (not 150 + 30 appended)
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(180L);
  }

  @Test
  void shouldDedupBySourceCoordinate() {
    // given
    rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));

    // when — the same source coordinate is redelivered
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush();

    // then — folded once
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
  }

  @Test
  void shouldFinalizeAndEvictClosedWindows() {
    // given — a sale in hour 0
    rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush();
    assertThat(rollup.openWindowCount()).isEqualTo(1);

    // when — event time advances past hour-0 end + lateness
    rollup.advanceStreamTime(HOUR + LATENESS + 1);

    // then — hour 0 emitted its final value and was evicted (state bounded)
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
    assertThat(rollup.openWindowCount()).isZero();
  }
}
