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
import io.camunda.zeebe.db.impl.DbBytes;
import org.junit.jupiter.api.Test;

final class MergingAggregationTest {

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

  @Test
  void shouldMergeAcrossWritersAndOverwriteAWritersSlotIdempotently() {
    // given — a merge operator over a shared slot store, serving into an in-memory sink
    final KeyValueStore<DbBytes, DbBytes> slots =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final MergingAggregation<String, Long> merge =
        new MergingAggregation<>(
            1,
            SUM,
            // grace keeps the window open so the writer-0 re-emit is not dropped as "closed"
            TumblingWindows.ofSizeAndGrace(1_000, 5_000),
            sink,
            slots,
            new StringRecordValue(),
            new LongRecordValue(),
            Runnable::run);
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when — two writers contribute to the same cell
    merge.accept(0, cell, 3L);
    merge.accept(1, cell, 4L);
    merge.flush();

    // then — the served value is the merge across writers
    assertThat(sink.get(cell)).hasValue(7L);

    // when — writer 0 re-emits (overwrites its own slot), not adds
    merge.accept(0, cell, 10L);
    merge.flush();

    // then — 10 (writer 0) + 4 (writer 1), proving the per-writer slot is idempotent
    assertThat(sink.get(cell)).hasValue(14L);
  }
}
