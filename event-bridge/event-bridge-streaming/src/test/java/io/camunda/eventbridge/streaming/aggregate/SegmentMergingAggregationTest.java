/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
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
    return merger(store, sink, windows, Runnable::run);
  }

  private static SegmentMergingAggregation<String, Long> merger(
      final KeyValueStore<DbBytes, DbBytes> store,
      final InMemoryResultSink<Windowed<String>, Long> sink,
      final Windows windows,
      final TransactionRunner tx) {
    return new SegmentMergingAggregation<>(
        1, SUM, windows, sink, store, new StringRecordValue(), new LongRecordValue(), tx);
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
  void shouldReportADroppedLateDeltaToTheListener() {
    // given a no-grace window and a wired late-drop observer
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            sink,
            TumblingWindows.of(1_000L));
    final List<Windowed<String>> dropped = new ArrayList<>();
    final List<Long> clocks = new ArrayList<>();
    merger.onLateDrop(
        (cell, eventTimeHint, maxEventTime) -> {
          dropped.add(cell);
          clocks.add(maxEventTime);
        });
    final Windowed<String> cell = new Windowed<>("k", 0L);

    // when the window's delta closes it and a late delta follows
    merger.merge(cell, 5L);
    merger.checkpoint();
    merger.merge(cell, 100L);
    merger.flush();

    // then only the late delta is observed, with the clock that had closed its window — and the
    // finalized value stays untouched
    assertThat(dropped).containsExactly(cell);
    assertThat(clocks).containsExactly(1_000L);
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

  @Test
  void shouldNotAliasACallerOwnedDeltaAsTheRunningTotal() {
    // given an aggregate with a mutable accumulator and an in-place mergeInto
    final InMemoryResultSink<Windowed<String>, long[]> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, long[]> merger =
        new SegmentMergingAggregation<>(
            1,
            MUTABLE_SUM,
            keepOpen(),
            sink,
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            new StringRecordValue(),
            new LongArrayValue(),
            Runnable::run);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    final long[] delta = {3L};

    // when the first delta is merged and the caller then reuses its own instance
    merger.merge(cell, delta);
    delta[0] = 100L;
    merger.merge(cell, new long[] {4L});
    merger.flush();

    // then the running total owns its state — the caller's later mutation did not leak in
    assertThat(sink.get(cell)).hasValueSatisfying(total -> assertThat(total[0]).isEqualTo(7L));
  }

  @Test
  void shouldSerializeAChangedCellOnceAcrossFlushAndCheckpoint() {
    // given a codec that counts serializations and a sink that receives the serialized form
    final CountingLongValue codec = new CountingLongValue();
    final Map<Windowed<String>, byte[]> served = new HashMap<>();
    final ResultSink<Windowed<String>, Long> sink =
        new ResultSink<>() {
          @Override
          public void upsert(final Windowed<String> key, final Long value) {
            fail("the aggregation should always hand the sink the serialized form");
          }

          @Override
          public void upsert(final Windowed<String> key, final Long value, final byte[] bytes) {
            served.put(key, bytes);
          }
        };
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            1, SUM, keepOpen(), sink, store, new StringRecordValue(), codec, Runnable::run);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 3L);
    merger.merge(cell, 4L);

    // when one commit runs (flush, then checkpoint — the runtime's produce-before-commit order)
    merger.flush();
    merger.checkpoint();

    // then the total was serialized exactly once, and the durable cell holds those same bytes
    assertThat(codec.serializations).isEqualTo(1);
    assertThat(codec.fromBytes(served.get(cell))).isEqualTo(7L);
    final List<byte[]> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(value.getBytes().clone()));
    assertThat(stored).singleElement().isEqualTo(served.get(cell));
  }

  @Test
  void shouldFinalizeOnlyTheDueWindowsAmongManyOpenCells() {
    // given many open cells across distinct windows (size 1s, grace 2s)
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, sink, TumblingWindows.ofSizeAndGrace(1_000L, 2_000L));
    for (int i = 0; i < 10; i++) {
      merger.merge(new Windowed<>("k" + i, i * 1_000L), i + 1L);
    }

    // when the watermark (maxEventTime 10s − grace 2s = 8s) passes a subset of the window ends
    merger.checkpoint();

    // then exactly the windows ending at or before the watermark were finalized and evicted from
    // the durable store; the still-open ones were checkpointed
    final List<Long> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(decodeWindowStart(key.getBytes())));
    assertThat(stored).containsExactlyInAnyOrder(8_000L, 9_000L);
    for (int i = 0; i < 8; i++) {
      assertThat(sink.get(new Windowed<>("k" + i, i * 1_000L))).hasValue(i + 1L);
    }

    // and an open cell still folds while a finalized cell drops late deltas
    merger.merge(new Windowed<>("k8", 8_000L), 10L);
    merger.merge(new Windowed<>("k0", 0L), 100L);
    merger.flush();
    assertThat(sink.get(new Windowed<>("k8", 8_000L))).hasValue(19L);
    assertThat(sink.get(new Windowed<>("k0", 0L))).hasValue(1L);
  }

  @Test
  void shouldRecoverTheFinalizationIndexFromTheStore() {
    // given two open cells checkpointed by a previous incarnation (grace keeps them open)
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final Windows windows = TumblingWindows.ofSizeAndGrace(1_000L, 2_000L);
    final SegmentMergingAggregation<String, Long> before =
        merger(store, new InMemoryResultSink<>(), windows);
    before.merge(new Windowed<>("a", 0L), 3L);
    before.merge(new Windowed<>("b", 1_000L), 4L);
    before.checkpoint();

    // when a fresh operator recovers and the watermark then passes the recovered windows' ends
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> after = merger(store, sink, windows);
    after.merge(new Windowed<>("c", 9_000L), 5L);
    after.checkpoint();

    // then the recovered cells were finalized off the rebuilt index and their durable cells
    // deleted; only the fresh open cell remains
    assertThat(sink.get(new Windowed<>("a", 0L))).hasValue(3L);
    assertThat(sink.get(new Windowed<>("b", 1_000L))).hasValue(4L);
    final List<Long> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(decodeWindowStart(key.getBytes())));
    assertThat(stored).containsExactly(9_000L);
  }

  @Test
  void shouldFinalizeADrainedCellAtItsWindowEndAndKeepTestingTheRest() {
    // given a drained predicate (total >= 10) and a grace long enough that time never closes them
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            1,
            SUM,
            TumblingWindows.ofSizeAndGrace(1_000L, 60_000L),
            sink,
            store,
            new StringRecordValue(),
            new LongRecordValue(),
            Runnable::run,
            acc -> acc >= 10L);
    final Windowed<String> undrained = new Windowed<>("slow", 0L);
    final Windowed<String> drained = new Windowed<>("fast", 1_000L);
    merger.merge(undrained, 3L);
    merger.merge(drained, 10L);

    // when both windows have ended (maxEventTime 2s) but only one accumulator has drained
    merger.checkpoint();

    // then the drained cell finalized at its window end (long before the grace backstop) and the
    // undrained one stayed open
    assertThat(sink.get(drained)).hasValue(10L);
    final List<Long> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(decodeWindowStart(key.getBytes())));
    assertThat(stored).containsExactly(0L);

    // and the still-indexed candidate finalizes once it drains on a later checkpoint
    merger.merge(undrained, 7L);
    merger.checkpoint();
    assertThat(sink.get(undrained)).hasValue(10L);
    final List<Long> remaining = new ArrayList<>();
    store.forEach((key, value) -> remaining.add(decodeWindowStart(key.getBytes())));
    assertThat(remaining).isEmpty();
  }

  @Test
  void shouldNotPersistStateAheadOfTheLastCheckpointOnClose() {
    // given a running total whose first delta was checkpointed (the owner's commit cut)
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final InMemoryResultSink<Windowed<String>, Long> sinkBefore = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> before = merger(store, sinkBefore, keepOpen());
    final Windowed<String> cell = new Windowed<>("k", 0L);
    before.merge(cell, 3L);
    before.checkpoint();

    // when a later delta folds and the operator closes gracefully WITHOUT a commit
    before.merge(cell, 4L);
    before.close();

    // then the close converged the (idempotent) serving view but the durable cell still matches
    // the last commit — not the close; a cell persisted ahead of the offset cut would be
    // double-folded when the uncommitted delta replays after restart
    assertThat(sinkBefore.get(cell)).hasValue(7L);
    final List<Long> stored = new ArrayList<>();
    final LongRecordValue codec = new LongRecordValue();
    store.forEach((key, value) -> stored.add(codec.fromBytes(value.getBytes())));
    assertThat(stored).containsExactly(3L);

    // and a fresh operator recovers the commit and folds the replayed delta exactly once
    final InMemoryResultSink<Windowed<String>, Long> sinkAfter = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> after = merger(store, sinkAfter, keepOpen());
    after.merge(cell, 4L);
    after.flush();
    assertThat(sinkAfter.get(cell)).hasValue(7L);
  }

  @Test
  void shouldPersistTheAtFreezeBytesWhenFoldsContinueAfterTheFreeze() {
    // given a frozen checkpoint delta capturing a total of 3
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, new InMemoryResultSink<>(), keepOpen());
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 3L);
    merger.freeze();

    // when the owner keeps folding while the frozen delta persists
    merger.merge(cell, 4L);
    merger.persistFrozen();
    merger.completeFrozen(true);

    // then the durable cell holds the at-freeze value, not the concurrent fold
    assertThat(storedTotals(store)).containsExactly(3L);

    // and the next cut persists the newer total
    merger.checkpoint();
    assertThat(storedTotals(store)).containsExactly(7L);
  }

  @Test
  void shouldLandAnEvictionAfterAPersistedCutInTheNextCut() {
    // given a cell persisted by one cut (grace keeps it open)
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, new InMemoryResultSink<>(), TumblingWindows.ofSizeAndGrace(1_000L, 2_000L));
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 5L);
    merger.checkpoint();
    assertThat(storedWindowStarts(store)).containsExactly(0L);

    // when event time closes the window and the next cut freezes the eviction
    merger.merge(new Windowed<>("later", 10_000L), 1L);
    merger.freeze();

    // then the durable row is untouched until that cut persists — and gone right after
    assertThat(storedWindowStarts(store)).containsExactly(0L);
    merger.persistFrozen();
    merger.completeFrozen(true);
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
  }

  @Test
  void shouldRetryAFailedCheckpointFromTheMergedBackDeltaWithoutReserializing() {
    // given a flushed total whose checkpoint transaction fails
    final CountingLongValue codec = new CountingLongValue();
    final AtomicBoolean failTransaction = new AtomicBoolean();
    final TransactionRunner tx =
        operations -> {
          if (failTransaction.get()) {
            throw new IllegalStateException("transaction failed");
          }
          operations.run();
        };
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            1,
            SUM,
            keepOpen(),
            new InMemoryResultSink<>(),
            store,
            new StringRecordValue(),
            codec,
            tx);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 3L);
    merger.flush();
    failTransaction.set(true);
    assertThatThrownBy(merger::checkpoint).hasMessage("transaction failed");
    assertThat(storedTotals(store)).as("the failed cut persisted nothing").isEmpty();

    // when the next checkpoint retries with nothing re-folded or re-flushed in between
    failTransaction.set(false);
    merger.checkpoint();

    // then the merged-back frozen bytes were reused — persisted without a second serialization
    assertThat(storedTotals(store)).containsExactly(3L);
    assertThat(codec.serializations).isEqualTo(1);
  }

  @Test
  void shouldServeTheLiveFoldNotTheStaleFrozenBytesAfterAFailedCut() {
    // given a frozen cut whose persist never ran, and a newer fold landing before its completion
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, sink, TumblingWindows.ofSizeAndGrace(1_000L, 2_000L));
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 3L);
    merger.freeze();
    merger.merge(cell, 4L);

    // when the cut fails and the window then closes on the next checkpoint
    merger.completeFrozen(false);
    merger.merge(new Windowed<>("later", 10_000L), 1L);
    merger.checkpoint();

    // then the finalized value is the live total — the stale frozen bytes did not shadow it
    assertThat(sink.get(cell)).hasValue(7L);
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
  }

  @Test
  void shouldDeleteAnEvictedCellOnRetryAfterAFailedCheckpoint() {
    // given a durable row whose eviction cut fails
    final AtomicBoolean failTransaction = new AtomicBoolean();
    final TransactionRunner tx =
        operations -> {
          if (failTransaction.get()) {
            throw new IllegalStateException("transaction failed");
          }
          operations.run();
        };
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, sink, TumblingWindows.ofSizeAndGrace(1_000L, 2_000L), tx);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 5L);
    merger.checkpoint();
    merger.merge(new Windowed<>("later", 10_000L), 1L); // closes the cell's window
    failTransaction.set(true);
    assertThatThrownBy(merger::checkpoint).hasMessage("transaction failed");
    assertThat(storedWindowStarts(store)).containsExactly(0L);

    // when a late delta for the evicted window arrives and the checkpoint retries
    merger.merge(cell, 100L); // dropped: the window closed and the cell was evicted
    failTransaction.set(false);
    merger.checkpoint();

    // then the merged-back eviction landed and the late delta did not resurrect the cell
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
    assertThat(sink.get(cell)).hasValue(5L);
  }

  @Test
  void shouldKeepAResurrectedCellWhoseEvictionWasNeverPersisted() {
    // given a drained cell evicted by a frozen cut that then fails
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        new SegmentMergingAggregation<>(
            1,
            SUM,
            TumblingWindows.ofSizeAndGrace(1_000L, 60_000L),
            sink,
            store,
            new StringRecordValue(),
            new LongRecordValue(),
            Runnable::run,
            acc -> acc >= 10L);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 10L); // drained — the freeze finalizes and evicts it
    merger.freeze();
    assertThat(sink.get(cell)).hasValue(10L);

    // when a late (still in grace) delta resurrects the cell before the failed cut completes
    merger.merge(cell, 5L);
    merger.completeFrozen(false);
    merger.checkpoint();

    // then the resurrected cell survives the retried cut — its row is written, not deleted
    assertThat(storedTotals(store)).containsExactly(5L);
  }

  @Test
  void shouldNotPersistADeleteForACellBornAndEvictedBetweenTwoCuts() {
    // given a completed cut, and a cell then born AND finalized before the next cut — no cut ever
    // wrote its row
    final RecordingKeyValueStore store =
        new RecordingKeyValueStore(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()));
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, sink, TumblingWindows.ofSizeAndGrace(1_000L, 2_000L));
    merger.checkpoint();
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 5L);
    merger.merge(new Windowed<>("later", 10_000L), 1L); // closes the cell's window

    // when the next cut finalizes and evicts the born-and-died cell
    merger.checkpoint();

    // then its window was still emitted downstream, but no delete was persisted — there was never
    // a durable row to remove
    assertThat(sink.get(cell)).hasValue(5L);
    assertThat(store.deleteWindowStarts()).isEmpty();
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
  }

  @Test
  void shouldPersistADeleteForAnEvictedCellAnEarlierCutWrote() {
    // given a cell whose row a completed cut persisted
    final RecordingKeyValueStore store =
        new RecordingKeyValueStore(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()));
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, new InMemoryResultSink<>(), TumblingWindows.ofSizeAndGrace(1_000L, 2_000L));
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 5L);
    merger.checkpoint();
    store.clearRecorded();

    // when event time closes the cell's window and the next cut evicts it
    merger.merge(new Windowed<>("later", 10_000L), 1L);
    merger.checkpoint();

    // then exactly that row's delete was persisted
    assertThat(store.deleteWindowStarts()).containsExactly(0L);
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
  }

  @Test
  void shouldNotPersistADeleteForACellOnlyAFailedCutCarried() {
    // given a cell frozen by a cut whose persist failed — it was never durably written
    final AtomicBoolean failTransaction = new AtomicBoolean();
    final TransactionRunner tx =
        operations -> {
          if (failTransaction.get()) {
            throw new IllegalStateException("transaction failed");
          }
          operations.run();
        };
    final RecordingKeyValueStore store =
        new RecordingKeyValueStore(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()));
    final InMemoryResultSink<Windowed<String>, Long> sink = new InMemoryResultSink<>();
    final SegmentMergingAggregation<String, Long> merger =
        merger(store, sink, TumblingWindows.ofSizeAndGrace(1_000L, 2_000L), tx);
    final Windowed<String> cell = new Windowed<>("k", 0L);
    merger.merge(cell, 5L);
    failTransaction.set(true);
    assertThatThrownBy(merger::checkpoint).hasMessage("transaction failed");
    assertThat(storedWindowStarts(store)).as("the failed cut persisted nothing").isEmpty();

    // when the cell's window closes and the next successful cut evicts it
    failTransaction.set(false);
    merger.merge(new Windowed<>("later", 10_000L), 1L);
    merger.checkpoint();

    // then no delete was persisted for it, and its merged-back data was not lost — the window
    // finalized downstream with the full total
    assertThat(store.deleteWindowStarts()).isEmpty();
    assertThat(sink.get(cell)).hasValue(5L);
    assertThat(storedWindowStarts(store)).containsExactly(10_000L);
  }

  @Test
  void shouldRejectOverlappingFreezesAndUnpairedPersistOrComplete() {
    // given an outstanding frozen delta
    final SegmentMergingAggregation<String, Long> merger =
        merger(
            new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()),
            new InMemoryResultSink<>(),
            keepOpen());
    merger.merge(new Windowed<>("k", 0L), 1L);
    merger.freeze();

    // when / then a second freeze is rejected while one is outstanding
    assertThatThrownBy(merger::freeze).isInstanceOf(IllegalStateException.class);

    // and once completed, persist/complete without a freeze are rejected too
    merger.persistFrozen();
    merger.completeFrozen(true);
    assertThatThrownBy(merger::persistFrozen).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> merger.completeFrozen(true)).isInstanceOf(IllegalStateException.class);
  }

  /** The {@code windowStart} of a durable cell key ({@code group ++ windowStart ++ key}). */
  private static long decodeWindowStart(final byte[] cellKey) {
    return ByteBuffer.wrap(cellKey).getLong(Integer.BYTES);
  }

  /** Every durable cell's total, decoded through the accumulator codec. */
  private static List<Long> storedTotals(final KeyValueStore<DbBytes, DbBytes> store) {
    final List<Long> stored = new ArrayList<>();
    final LongRecordValue codec = new LongRecordValue();
    store.forEach((key, value) -> stored.add(codec.fromBytes(value.getBytes())));
    return stored;
  }

  /** Every durable cell's {@code windowStart}, identifying which cells have rows. */
  private static List<Long> storedWindowStarts(final KeyValueStore<DbBytes, DbBytes> store) {
    final List<Long> stored = new ArrayList<>();
    store.forEach((key, value) -> stored.add(decodeWindowStart(key.getBytes())));
    return stored;
  }

  /** A window wide enough with grace that it stays open across the test. */
  private static Windows keepOpen() {
    return TumblingWindows.ofSizeAndGrace(1_000L, 5_000L);
  }

  /** A sum over a mutable accumulator, with the in-place {@code mergeInto} the sketches use. */
  private static final AggregateFunction<Object, long[], Long> MUTABLE_SUM =
      new AggregateFunction<>() {
        @Override
        public long[] createAccumulator() {
          return new long[1];
        }

        @Override
        public long[] add(final Object item, final long[] acc) {
          return acc;
        }

        @Override
        public long[] merge(final long[] a, final long[] b) {
          return new long[] {a[0] + b[0]};
        }

        @Override
        public long[] mergeInto(final long[] target, final long[] delta) {
          target[0] += delta[0];
          return target;
        }

        @Override
        public Long getResult(final long[] acc) {
          return acc[0];
        }
      };

  /** Serializes a {@code long[1]} accumulator through {@link DbLong}. */
  private static final class LongArrayValue implements RecordValue<long[]> {
    private final DbLong delegate = new DbLong();

    @Override
    public RecordValue<long[]> wrapValue(final long[] value) {
      delegate.wrapLong(value[0]);
      return this;
    }

    @Override
    public long[] value() {
      return new long[] {delegate.getValue()};
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      delegate.wrap(buffer, offset, length);
    }

    @Override
    public int getLength() {
      return delegate.getLength();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return delegate.write(buffer, offset);
    }
  }

  /** A {@link LongRecordValue}-shaped codec that counts {@link #toBytes} calls. */
  private static final class CountingLongValue implements RecordValue<Long> {
    private final LongRecordValue delegate = new LongRecordValue();
    private int serializations;

    @Override
    public RecordValue<Long> wrapValue(final Long value) {
      delegate.wrapValue(value);
      return this;
    }

    @Override
    public Long value() {
      return delegate.value();
    }

    @Override
    public byte[] toBytes(final Long value) {
      serializations++;
      return RecordValue.super.toBytes(value);
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      delegate.wrap(buffer, offset, length);
    }

    @Override
    public int getLength() {
      return delegate.getLength();
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return delegate.write(buffer, offset);
    }
  }
}
