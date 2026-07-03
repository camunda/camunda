/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import org.junit.jupiter.api.Test;

final class SegmentMergingAggregationTest {

  private static final AggregateFunction<Object, Long, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Long createAccumulator() {
          return 0L;
        }

        @Override
        public Long add(final Object item, final Long acc) {
          return acc;
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

  private static SegmentMergingAggregation<String, Long> merger(
      final KeyValueStore<DbBytes, DbBytes> store,
      final InMemoryResultSink<Windowed<String>, Long> sink,
      final Windows windows) {
    return new SegmentMergingAggregation<>(
        1,
        SUM,
        windows,
        sink,
        store,
        new StringRecordValue(),
        new LongRecordValue(),
        Runnable::run);
  }

  @Test
  void shouldFoldDeltasIntoARunningTotal() {
    // given
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()), sink, keepOpen());
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when two segment deltas for the cell are merged
    merger.merge(cell, 3L);
    merger.merge(cell, 4L);
    merger.flush();

    // then the served value is their sum (deltas ADD into one running total, unlike per-writer
    // slots)
    assertThat(sink.get(cell)).hasValue(7L);

    // when a third delta arrives
    merger.merge(cell, 10L);
    merger.flush();

    // then it folds in — 7 + 10
    assertThat(sink.get(cell)).hasValue(17L);
  }

  @Test
  void shouldFinalizeClosedWindowsAndDropLateDeltas() {
    // given a no-grace window so it closes as soon as event time passes its end
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            sink,
            TumblingWindows.of(1_000L));
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when the window's delta is merged and a checkpoint runs (event time reached the window end)
    merger.merge(cell, 5L);
    merger.checkpoint();

    // then the window is finalized to its value
    assertThat(sink.get(cell)).hasValue(5L);

    // and a late delta for the now-evicted window is dropped, not resurrected
    merger.merge(cell, 100L);
    merger.flush();
    assertThat(sink.get(cell)).hasValue(5L);
  }

  @Test
  void shouldRecoverRunningTotalsFromTheStore() {
    // given a running total checkpointed to a shared store
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final Windowed<String> cell = new Windowed<>("k", 0L);
    final SegmentMergingAggregation<String, Long> before =
        merger(store, new InMemoryResultSink<>(), keepOpen());
    before.merge(cell, 6L);
    before.checkpoint();

    // when a fresh operator recovers from the same store (a restart)
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> after = merger(store, sink, keepOpen());
    after.merge(cell, 4L);
    after.flush();

    // then it continued from the recovered total — 6 + 4
    assertThat(sink.get(cell)).hasValue(10L);
  }

  /** A window wide enough with grace that it stays open across the test. */
  private static Windows keepOpen() {
    return TumblingWindows.ofSizeAndGrace(1_000L, 5_000L);
  }
}
