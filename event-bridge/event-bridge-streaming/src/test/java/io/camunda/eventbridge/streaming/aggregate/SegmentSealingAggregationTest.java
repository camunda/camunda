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

import io.camunda.eventbridge.streaming.TransactionRunner;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.zeebe.db.impl.DbBytes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class SegmentSealingAggregationTest {

  /** A minimal fold input carrying its own source coordinate, event time, key and value. */
  private record Ev(int partition, long position, long eventTime, String key, long value) {}

  /** One delta the aggregation sealed. */
  private record Sealed(String key, long windowStart, int partition, long segment, long delta) {}

  private static final SourceCoordinate<Ev> COORDINATE =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Ev value) {
          return value.partition();
        }

        @Override
        public long position(final Ev value) {
          return value.position();
        }
      };

  private final List<Sealed> emitted = new ArrayList<>();

  /** Stride 10, and one huge window so the test isolates segment (not window) behaviour. */
  private SegmentSealingAggregation<Ev, String, Long> aggregation() {
    return new SegmentSealingAggregation<>(
        new SumAggregateFunction<>(Ev::value),
        Ev::key,
        COORDINATE,
        Ev::eventTime,
        TumblingWindows.of(1_000_000L),
        Segments.ofStride(10L),
        sink());
  }

  /** A durable (Model-F) aggregation checkpointing its open segment to {@code store}. */
  private SegmentSealingAggregation<Ev, String, Long> durable(
      final KeyValueStore<DbBytes, DbBytes> store) {
    return durable(store, Runnable::run);
  }

  private SegmentSealingAggregation<Ev, String, Long> durable(
      final KeyValueStore<DbBytes, DbBytes> store, final TransactionRunner tx) {
    return new SegmentSealingAggregation<>(
        1,
        new SumAggregateFunction<>(Ev::value),
        Ev::key,
        COORDINATE,
        Ev::eventTime,
        TumblingWindows.of(1_000_000L),
        Segments.ofStride(10L),
        sink(),
        store,
        new StringRecordValue(),
        new LongRecordValue(),
        tx);
  }

  private SegmentSink<String, Long> sink() {
    return (cell, partition, segment, delta) ->
        emitted.add(new Sealed(cell.key(), cell.windowStart(), partition, segment, delta));
  }

  @Test
  void shouldSealCompletedSegmentAsFromEmptyDelta() {
    // given
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();

    // when two records land in segment 0 and a third crosses into segment 1
    aggregation.accept(new Ev(0, 0L, 100L, "a", 5L));
    aggregation.accept(new Ev(0, 5L, 100L, "a", 3L));
    assertThat(emitted).as("nothing sealed while segment 0 is still open").isEmpty();
    aggregation.accept(new Ev(0, 10L, 100L, "a", 100L));

    // then segment 0 is sealed as its own from-empty delta (5 + 3), segment 1 stays open
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 8L));
    assertThat(aggregation.safeOffset()).isEqualTo(9L); // everything before segment 1
  }

  @Test
  void shouldSealEachKeySeparately() {
    // given
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();

    // when two keys accumulate in segment 0, then a record opens segment 1
    aggregation.accept(new Ev(0, 1L, 100L, "a", 2L));
    aggregation.accept(new Ev(0, 2L, 100L, "b", 7L));
    aggregation.accept(new Ev(0, 12L, 100L, "a", 1L));

    // then one delta per cell
    assertThat(emitted)
        .containsExactlyInAnyOrder(new Sealed("a", 0L, 0, 0L, 2L), new Sealed("b", 0L, 0, 0L, 7L));
  }

  @Test
  void shouldNotSealOpenSegmentOnFlushOrClose() throws Exception {
    // given records only in segment 0
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();
    aggregation.accept(new Ev(0, 3L, 100L, "a", 4L));

    // when the freshness/shutdown paths run
    aggregation.flush();
    aggregation.close();

    // then the open segment is not sealed (it replays on restart) and no offset is safe yet
    assertThat(emitted).isEmpty();
    assertThat(aggregation.safeOffset()).isEqualTo(SegmentSealingAggregation.NO_OFFSET);
  }

  @Test
  void shouldAdvanceSafeOffsetPastSkippedEmptySegments() {
    // given a jump from segment 0 straight to segment 2 (segment 1 sees no records)
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();
    aggregation.accept(new Ev(0, 5L, 100L, "a", 1L));
    aggregation.accept(new Ev(0, 25L, 100L, "a", 1L));

    // then segment 0 sealed, and safe offset covers the skipped-empty segment 1 too
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 1L));
    assertThat(aggregation.safeOffset()).isEqualTo(19L); // start of segment 2, minus one
  }

  @Test
  void shouldSealOpenSegmentWhenTheWatermarkAdvancesPastIt() {
    // given a record folded into segment 0, then the stream goes quiet (no boundary crossing)
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();
    aggregation.accept(new Ev(0, 3L, 100L, "a", 4L));

    // when the watermark is still inside segment 0, nothing seals
    aggregation.sealCompletedUpTo(9L);
    assertThat(emitted).as("segment 0 not yet fully behind the watermark").isEmpty();

    // when the watermark advances into a later segment, the completed segment 0 seals on its own
    aggregation.sealCompletedUpTo(10L);
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 4L));

    // and a later record opens a fresh segment — the sealed one was closed, not re-emitted
    emitted.clear();
    aggregation.accept(new Ev(0, 12L, 100L, "a", 6L));
    aggregation.sealCompletedUpTo(25L);
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 1L, 6L));
  }

  @Test
  void shouldBeDeterministicAcrossReplay() {
    // given the same input fed to two independent aggregations (a replay)
    final List<Ev> input =
        List.of(
            new Ev(0, 0L, 100L, "a", 5L),
            new Ev(0, 4L, 100L, "b", 2L),
            new Ev(0, 7L, 100L, "a", 3L),
            new Ev(0, 11L, 100L, "a", 100L));

    final List<Sealed> first = replay(input);
    final List<Sealed> second = replay(input);

    // then identical sealed deltas
    assertThat(second).isEqualTo(first);
    assertThat(first)
        .containsExactlyInAnyOrder(new Sealed("a", 0L, 0, 0L, 8L), new Sealed("b", 0L, 0, 0L, 2L));
  }

  private List<Sealed> replay(final List<Ev> input) {
    emitted.clear();
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();
    input.forEach(aggregation::accept);
    return List.copyOf(emitted);
  }

  @Test
  void shouldRestoreOpenSegmentFromCheckpoint() {
    // given a durable aggregation that folded two records into the still-open segment 0 and
    // checkpointed (Model F: the full offset would commit, so the open partial must survive)
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentSealingAggregation<Ev, String, Long> before = durable(store);
    before.accept(new Ev(0, 0L, 100L, "a", 5L));
    before.accept(new Ev(0, 5L, 100L, "a", 3L));
    before.checkpoint();
    assertThat(emitted).as("segment 0 still open, nothing sealed").isEmpty();

    // when a fresh aggregation recovers from the same store (a crash + replay-from-committed) and a
    // record crosses into segment 1
    final SegmentSealingAggregation<Ev, String, Long> after = durable(store);
    after.accept(new Ev(0, 10L, 100L, "a", 100L));

    // then the recovered open partial (5 + 3) is included in segment 0's sealed delta — not lost
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 8L));
  }

  @Test
  void shouldClearSealedCellsFromTheCheckpointOnTheNextCheckpoint() {
    // given a durable aggregation that sealed segment 0 (its cells cleared from the open buffer)
    // and
    // opened segment 1 with a new partial
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentSealingAggregation<Ev, String, Long> before = durable(store);
    before.accept(new Ev(0, 0L, 100L, "a", 5L)); // segment 0
    before.accept(new Ev(0, 10L, 100L, "b", 7L)); // seals segment 0, opens segment 1
    before.checkpoint();
    emitted.clear();

    // when recovering and crossing into segment 2
    final SegmentSealingAggregation<Ev, String, Long> after = durable(store);
    after.accept(new Ev(0, 20L, 100L, "b", 1L));

    // then only segment 1's open partial ("b" = 7) is sealed — segment 0's cells were not
    // resurrected
    assertThat(emitted).containsExactly(new Sealed("b", 0L, 0, 1L, 7L));
  }

  @Test
  void shouldPersistTheAtFreezeOpenBufferWhenFoldsContinueAfterTheFreeze() {
    // given a durable aggregation frozen mid-segment
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentSealingAggregation<Ev, String, Long> before = durable(store);
    before.accept(new Ev(0, 0L, 100L, "a", 5L));
    before.freeze();

    // when the owner keeps folding the open segment while the frozen snapshot persists
    before.accept(new Ev(0, 5L, 100L, "a", 3L));
    before.persistFrozen();
    before.completeFrozen(true);

    // then a recovery sees exactly the at-freeze partial — the later fold replays from the cut
    final SegmentSealingAggregation<Ev, String, Long> after = durable(store);
    after.accept(new Ev(0, 10L, 100L, "a", 100L)); // seals segment 0
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 5L));
  }

  @Test
  void shouldPersistTheFrozenSegmentEvenWhenItSealsBeforeThePersist() {
    // given a frozen open segment
    final KeyValueStore<DbBytes, DbBytes> store =
        new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes());
    final SegmentSealingAggregation<Ev, String, Long> before = durable(store);
    before.accept(new Ev(0, 0L, 100L, "a", 5L));
    before.freeze();

    // when a crossing record seals it (clearing the live buffer) before the snapshot persists
    before.accept(new Ev(0, 10L, 100L, "b", 7L));
    before.persistFrozen();
    before.completeFrozen(true);
    emitted.clear();

    // then recovery restores the frozen segment-0 partial and the replayed crossing record
    // re-seals it intact
    final SegmentSealingAggregation<Ev, String, Long> after = durable(store);
    after.accept(new Ev(0, 10L, 100L, "b", 7L));
    assertThat(emitted).containsExactly(new Sealed("a", 0L, 0, 0L, 5L));
  }

  @Test
  void shouldNotLoseStaleRowDeletionsWhenACheckpointFails() {
    // given segment 0 checkpointed durably and then sealed away
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
    final SegmentSealingAggregation<Ev, String, Long> before = durable(store, tx);
    before.accept(new Ev(0, 0L, 100L, "a", 5L));
    before.checkpoint();
    before.accept(new Ev(0, 10L, 100L, "b", 7L)); // seals segment 0, opens segment 1

    // when the next checkpoint fails and a later one retries
    failTransaction.set(true);
    assertThatThrownBy(before::checkpoint).hasMessage("transaction failed");
    failTransaction.set(false);
    before.checkpoint();

    // then recovery sees only segment 1's open partial — the stale segment-0 row was deleted by
    // the retried cut, not lost with the failed one
    emitted.clear();
    final SegmentSealingAggregation<Ev, String, Long> after = durable(store);
    after.accept(new Ev(0, 20L, 100L, "b", 1L));
    assertThat(emitted).containsExactly(new Sealed("b", 0L, 0, 1L, 7L));
  }

  @Test
  void shouldTreatTheSplitCheckpointAsANoOpWithoutDurableState() {
    // given a Model R aggregation with an open partial
    final SegmentSealingAggregation<Ev, String, Long> aggregation = aggregation();
    aggregation.accept(new Ev(0, 3L, 100L, "a", 4L));

    // when the split checkpoint steps run
    aggregation.freeze();
    aggregation.persistFrozen();
    aggregation.completeFrozen(true);

    // then nothing seals and nothing becomes durable — the open buffer replays instead
    assertThat(emitted).isEmpty();
    assertThat(aggregation.safeOffset()).isEqualTo(SegmentSealingAggregation.NO_OFFSET);
  }

  @Test
  void shouldRejectOverlappingFreezesAndUnpairedPersistOrComplete() {
    // given a durable aggregation with an outstanding frozen snapshot
    final SegmentSealingAggregation<Ev, String, Long> aggregation =
        durable(new InMemoryKeyValueStore<>(new DbBytes(), new DbBytes()));
    aggregation.accept(new Ev(0, 0L, 100L, "a", 5L));
    aggregation.freeze();

    // when / then a second freeze is rejected while one is outstanding
    assertThatThrownBy(aggregation::freeze).isInstanceOf(IllegalStateException.class);

    // and once completed, persist/complete without a freeze are rejected too
    aggregation.persistFrozen();
    aggregation.completeFrozen(true);
    assertThatThrownBy(aggregation::persistFrozen).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> aggregation.completeFrozen(true))
        .isInstanceOf(IllegalStateException.class);
  }
}
