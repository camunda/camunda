/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableLong;
import org.junit.jupiter.api.Test;

/**
 * Capstone: seal (Stage 1) -> dedup -> merge (Stage 2) end to end. Proves the segment-delta
 * pipeline (a) equals a one-pass fold of the same records, and (b) is exactly-once — a re-emitted
 * segment batch (a producer replay) does not double-count.
 */
final class SegmentDeltaPipelineTest {

  private record Ev(int partition, long position, long eventTime, String key, long value) {}

  /** A sealed segment's batch: the deltas emitted together for one (partition, segment). */
  private record Batch(int partition, long segment, List<Cell> cells) {}

  private record Cell(Windowed<String> cell, long delta) {}

  private static final AggregateFunction<Ev, MutableLong, Long> SUM =
      new SumAggregateFunction<>(Ev::value);
  private static final Windows WINDOW = TumblingWindows.ofSizeAndGrace(1_000_000L, 1_000_000L);

  @Test
  void shouldEqualOnePassFoldAndBeExactlyOnceUnderReemit() {
    // given a stream across two full segments (sealed) plus a trigger record that opens a third
    final List<Ev> records =
        List.of(
            new Ev(0, 0L, 100L, "a", 5L),
            new Ev(0, 3L, 100L, "a", 2L),
            new Ev(0, 5L, 100L, "b", 7L), // segment 0
            new Ev(0, 10L, 100L, "a", 1L),
            new Ev(0, 15L, 100L, "b", 3L), // segment 1
            new Ev(0, 20L, 100L, "a", 1000L)); // segment 2 — only opens it (never sealed)

    // reference: a one-pass fold over the records whose segments actually seal (0 and 1)
    final Windowed<String> a = new Windowed<>("a", 0L);
    final Windowed<String> b = new Windowed<>("b", 0L);
    // a = 5 + 2 + 1 = 8 ; b = 7 + 3 = 10 ; the segment-2 trigger (1000) is never sealed

    // when the records flow through seal -> dedup -> merge
    final List<Batch> batches = seal(records);
    final SegmentDedup dedup = new SegmentDedup();
    final InMemoryResultSink<Windowed<String>, MutableLong> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, MutableLong> merge = merger(sink);
    applyAll(batches, dedup, merge);
    merge.flush();

    // then it matches the one-pass reference
    assertThat(sink.get(a)).hasValue(new MutableLong(8));
    assertThat(sink.get(b)).hasValue(new MutableLong(10));

    // when segment 0's batch is re-emitted (a producer replay) and reapplied
    final Batch segmentZero = batches.get(0);
    applyAll(List.of(segmentZero), dedup, merge);
    merge.flush();

    // then dedup drops it wholesale — no double-count
    assertThat(sink.get(a)).hasValue(new MutableLong(8));
    assertThat(sink.get(b)).hasValue(new MutableLong(10));
  }

  /** Runs the sealing combiner over the records, capturing one batch per sealed segment. */
  private static List<Batch> seal(final List<Ev> records) {
    final List<Batch> batches = new ArrayList<>();
    final List<Cell> current = new ArrayList<>();
    final int[] partition = {-1};
    final long[] segment = {-1};
    final SegmentSealingAggregation<Ev, String, MutableLong> sealing =
        new SegmentSealingAggregation<>(
            SUM,
            Ev::key,
            new SourceCoordinate<>() {
              @Override
              public int partition(final Ev value) {
                return value.partition();
              }

              @Override
              public long position(final Ev value) {
                return value.position();
              }
            },
            Ev::eventTime,
            WINDOW,
            Segments.ofStride(10L),
            new SegmentSink<>() {
              @Override
              public void emit(
                  final Windowed<String> cell,
                  final int sourcePartition,
                  final long seg,
                  final MutableLong delta) {
                partition[0] = sourcePartition;
                segment[0] = seg;
                current.add(new Cell(cell, delta.value));
              }

              @Override
              public void flush() {
                if (!current.isEmpty()) {
                  batches.add(new Batch(partition[0], segment[0], List.copyOf(current)));
                  current.clear();
                }
              }
            });
    records.forEach(sealing::accept);
    return batches;
  }

  private static void applyAll(
      final List<Batch> batches,
      final SegmentDedup dedup,
      final SegmentMergingAggregation<String, MutableLong> merge) {
    for (final Batch batch : batches) {
      // A single-stream pipeline: streamId is fixed (the multiplexing/per-stream dedup is covered
      // by SegmentDedupTest).
      if (dedup.admit(batch.partition(), 0, batch.segment(), 0)) {
        for (final Cell cell : batch.cells()) {
          merge.merge(cell.cell(), new MutableLong(cell.delta()));
        }
      }
    }
  }

  private static SegmentMergingAggregation<String, MutableLong> merger(
      final InMemoryResultSink<Windowed<String>, MutableLong> sink) {
    return new SegmentMergingAggregation<>(
        1,
        SUM,
        WINDOW,
        sink,
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
        new StringRecordValue(),
        new MutableLongRecordValue(),
        Runnable::run);
  }
}
