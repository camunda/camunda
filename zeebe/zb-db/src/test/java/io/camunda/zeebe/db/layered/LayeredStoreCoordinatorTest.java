/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.MergeRound;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.segment.FlatSegment;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Coordinator orchestration: view publication, persist rounds, anchor atomicity. */
final class LayeredStoreCoordinatorTest {

  private static final String STORE_A = "store-a";
  private static final String STORE_B = "store-b";

  private final InMemoryDurableState state = new InMemoryDurableState();
  private final CountingSink sink = new CountingSink(state.sink());
  private final List<ReadOnlyView> views = new ArrayList<>();

  private LayeredKeyValueStore newStore(final String name) {
    return new LayeredKeyValueStore(name, state.store(name), 1024 * 1024, false, 4);
  }

  private LayeredStoreCoordinator newCoordinator(final LayeredKeyValueStore... stores) {
    return new LayeredStoreCoordinator(
        List.of(stores), sink, state.snapshotSource(), views::add, LayeredStoreMetrics.noop());
  }

  @Test
  void shouldPublishInitialViewOverCommittedState() {
    // given -- state committed before the coordinator exists
    state.store(STORE_A).put(bytes(1), bytes(10));

    // when
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // then
    assertThat(views).hasSize(1);
    final ReadOnlyView view = coordinator.currentView();
    assertThat(view).isSameAs(views.get(0));
    assertThat(view.get(STORE_A, bytes(1))).containsExactly(bytes(10));
    assertThat(view.exists(STORE_A, bytes(1))).isTrue();
    assertThat(view.get(STORE_A, bytes(2))).isNull();
    assertThat(view.exists(STORE_A, bytes(2))).isFalse();
  }

  @Test
  void shouldRejectUnknownStoreNameInView() {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // when / then
    assertThatThrownBy(() -> coordinator.currentView().get("no-such-store", bytes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-store");
  }

  @Test
  void shouldRejectDuplicateStoreNames() {
    // when / then
    assertThatThrownBy(() -> newCoordinator(newStore(STORE_A), newStore(STORE_A)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(STORE_A);
  }

  @Test
  void shouldPublishFrozenWritesInView() {
    // given -- a committed key that the frozen batch deletes, plus a fresh key
    state.store(STORE_A).put(bytes(1), bytes(10));
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);

    // when
    store.put(bytes(2), bytes(20));
    store.delete(bytes(1));
    store.promote();
    coordinator.freezeAll(5);

    // then -- the refreshed view resolves the segment over the pinned snapshot
    final ReadOnlyView view = coordinator.currentView();
    assertThat(view.get(STORE_A, bytes(2))).containsExactly(bytes(20));
    assertThat(view.get(STORE_A, bytes(1))).isNull();
    assertThat(view.exists(STORE_A, bytes(1))).isFalse();
  }

  @Test
  void shouldScanViewMergingSegmentsAndSnapshotWithinPrefixBounds() {
    // given -- committed keys inside and outside the prefix, then frozen writes that overwrite,
    // add and tombstone within the prefix
    state.store(STORE_A).put(bytes(1, 0), bytes(10));
    state.store(STORE_A).put(bytes(1, 2), bytes(12));
    state.store(STORE_A).put(bytes(2), bytes(20));
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);

    store.put(bytes(1, 0), bytes(99)); // shadows the committed value on an equal key
    store.put(bytes(1, 1), bytes(11)); // segment-only key between two committed ones
    store.delete(bytes(1, 2)); // tombstone hides the committed value
    store.promote();
    coordinator.freezeAll(5);

    // when
    final List<byte[]> keys = new ArrayList<>();
    final List<byte[]> values = new ArrayList<>();
    coordinator
        .currentView()
        .prefixScan(
            STORE_A,
            bytes(1),
            (key, value) -> {
              keys.add(key);
              values.add(value);
            });

    // then
    assertThat(keys).containsExactly(bytes(1, 0), bytes(1, 1));
    assertThat(values).containsExactly(bytes(99), bytes(11));

    // when -- an empty prefix scans everything, including snapshot-only keys
    final List<byte[]> allKeys = new ArrayList<>();
    coordinator.currentView().prefixScan(STORE_A, new byte[0], (key, value) -> allKeys.add(key));

    // then
    assertThat(allKeys).containsExactly(bytes(1, 0), bytes(1, 1), bytes(2));
  }

  @Test
  void shouldPersistMergedFinalVersionsAndAdvanceAnchor() throws Exception {
    // given -- a committed key the round deletes, plus two freezes with an overwrite in between
    state.store(STORE_A).put(bytes(9), bytes(90));
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final ReadOnlyView oldView = coordinator.currentView();

    // read-then-delete, as the processing path does: the read caches the committed key, which is
    // what marks the later tombstone as flushed so it reaches the delegate
    assertThat(store.get(bytes(9))).containsExactly(bytes(90));
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);

    store.put(bytes(1), bytes(11));
    store.delete(bytes(9));
    store.promote();
    coordinator.freezeAll(20);

    // when
    final PersistRound round = coordinator.prepareRound(30);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- the delegate holds exactly the merged final versions, cut at the newest watermark
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(11));
    assertThat(state.committedValue(STORE_A, bytes(9))).isNull();
    assertThat(round.anchor()).isEqualTo(20);
    assertThat(sink.readAnchor()).isEqualTo(20);
    assertThat(coordinator.persistedAnchor()).isEqualTo(20);
    assertThat(coordinator.roundOutstanding()).isFalse();

    // then -- the pipelines drained and the fresh view reads through the rotated snapshot
    assertThat(store.segmentsNewestFirst()).isEmpty();
    final ReadOnlyView freshView = coordinator.currentView();
    assertThat(freshView.get(STORE_A, bytes(1))).containsExactly(bytes(11));
    assertThat(freshView.get(STORE_A, bytes(9))).isNull();

    // then -- the old view still reads its own consistent cut: the durably deleted key is still
    // alive (no ghost) and the key created after its freeze is invisible (no phantom)
    assertThat(oldView.get(STORE_A, bytes(9))).containsExactly(bytes(90));
    assertThat(oldView.get(STORE_A, bytes(1))).isNull();
  }

  @Test
  void shouldRejectRoundWhileMergeOutstandingAndProceedAfterIt() throws Exception {
    // given -- an over-limit pipeline captured into a merge round
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    freezeDisjointBatches(store, coordinator, 5);
    final MergeRound merge = coordinator.prepareMerge();
    assertThat(merge).isNotNull();
    assertThat(coordinator.mergeOutstanding()).isTrue();

    // when / then -- a round captures every pipeline segment, so it must not start now
    assertThatThrownBy(() -> coordinator.prepareRound(10))
        .isInstanceOf(IllegalStateException.class);

    // when -- the merge (which may have run on an IO thread) completes
    merge.merge();
    coordinator.completeMerge(merge, true);

    // then -- one merged segment, and the round proceeds over it
    assertThat(store.segmentsNewestFirst()).hasSize(1);
    final PersistRound round = coordinator.prepareRound(10);
    round.persist();
    coordinator.completeRound(round, true);
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(10));
    assertThat(state.committedValue(STORE_A, bytes(5))).containsExactly(bytes(50));
  }

  @Test
  void shouldMergeWhileRoundOutstandingOverDisjointSegments() throws Exception {
    // given -- a round outstanding over one frozen segment
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(9), bytes(90));
    store.promote();
    final PersistRound round = coordinator.prepareRound(1);

    // and an over-limit non-persisting run frozen while it drains
    freezeDisjointBatches(store, coordinator, 5);
    final MergeRound merge = coordinator.prepareMerge();
    assertThat(merge).isNotNull();
    merge.merge();
    round.persist();

    // when -- the round completes (dropping its tail) before the merge is swapped in
    coordinator.completeRound(round, true);
    coordinator.completeMerge(merge, true);

    // then -- the merged run replaced exactly the non-persisting segments
    assertThat(store.segmentsNewestFirst()).hasSize(1);
    assertThat(store.get(bytes(1))).containsExactly(bytes(10));
    assertThat(state.committedValue(STORE_A, bytes(9))).containsExactly(bytes(90));
  }

  @Test
  void shouldKeepRunsAndRetryAfterFailedOrAbortedMerge() {
    // given -- a captured merge round
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    freezeDisjointBatches(store, coordinator, 5);
    final MergeRound failed = coordinator.prepareMerge();
    assertThat(failed).isNotNull();
    failed.merge();

    // when -- it completes as failed (the built results are released, the runs stay)
    coordinator.completeMerge(failed, false);

    // then -- nothing merged, everything readable, and a retry captures the same runs
    assertThat(store.segmentsNewestFirst()).hasSize(5);
    assertThat(store.get(bytes(3))).containsExactly(bytes(30));
    final MergeRound retry = coordinator.prepareMerge();
    assertThat(retry).isNotNull();

    // when -- a successor aborts the (now stale) retry without running it
    coordinator.abortOutstandingMerge();

    // then -- still unmerged and consistent; the next merge proceeds normally
    assertThat(coordinator.mergeOutstanding()).isFalse();
    assertThat(store.segmentsNewestFirst()).hasSize(5);
    final MergeRound next = coordinator.prepareMerge();
    assertThat(next).isNotNull();
    next.merge();
    coordinator.completeMerge(next, true);
    assertThat(store.segmentsNewestFirst()).hasSize(1);
    assertThat(store.get(bytes(3))).containsExactly(bytes(30));
  }

  @Test
  void shouldReturnNoMergeRoundWhenNoStoreNeedsOne() {
    // given -- a pipeline within its segment limit
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(1);

    // when / then
    assertThat(coordinator.prepareMerge()).isNull();
    assertThat(coordinator.mergeOutstanding()).isFalse();
  }

  /** Freezes {@code batches} disjoint single-key batches (key i -> value 10*i). */
  private static void freezeDisjointBatches(
      final LayeredKeyValueStore store,
      final LayeredStoreCoordinator coordinator,
      final int batches) {
    for (int i = 1; i <= batches; i++) {
      store.put(bytes(i), bytes(10 * i));
      store.promote();
      coordinator.freezeAll(i);
    }
  }

  @Test
  void shouldKeepDelegateAndAnchorUntouchedOnFailedRoundAndRetrySameSegments() throws Exception {
    // given
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);
    state.failNextCommit();

    // when -- the commit fails mid-round
    final PersistRound failedRound = coordinator.prepareRound(11);
    assertThatThrownBy(failedRound::persist).isInstanceOf(IllegalStateException.class);
    coordinator.completeRound(failedRound, false);

    // then -- nothing moved: no state, no anchor, segments still in the pipeline
    assertThat(state.committedValue(STORE_A, bytes(1))).isNull();
    assertThat(sink.readAnchor()).isEqualTo(-1);
    assertThat(coordinator.roundOutstanding()).isFalse();
    assertThat(store.segmentsNewestFirst()).hasSize(1);

    // when -- the next round retries
    final PersistRound retry = coordinator.prepareRound(12);

    // then -- it captured the same segments, so it commits the same cut
    assertThat(retry.anchor()).isEqualTo(failedRound.anchor()).isEqualTo(10);
    retry.persist();
    coordinator.completeRound(retry, true);
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(10));
    assertThat(sink.readAnchor()).isEqualTo(10);
  }

  // ------------------------------------------------------------------
  // Paced (sliced) drains
  // ------------------------------------------------------------------

  @Test
  void shouldDeferDesignatedAnchorEntryToFinalSlice() throws Exception {
    // given a store whose key 9 carries the recovery anchor (the real wiring's last-processed
    // position is a normal drained key, not a separate sink cell)
    final InMemoryDurableState anchorState = new InMemoryDurableState();
    final RecordingSink recordingSink = new RecordingSink(anchorState.sink());
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE_A, anchorState.store(STORE_A), 1024 * 1024, false, 4);
    try (final LayeredStoreCoordinator coordinator =
        new LayeredStoreCoordinator(
            List.of(store),
            recordingSink,
            anchorState.snapshotSource(),
            view -> {},
            LayeredStoreMetrics.noop())) {
      coordinator.designateAnchorEntry(STORE_A, bytes(9));
      for (int key = 1; key <= 5; key++) {
        store.put(bytes(key), bytes(10 * key));
      }
      store.put(bytes(9), bytes(90));
      store.promote();

      // when the round drains in single-entry slices
      final PersistRound round = coordinator.prepareRound(10);
      while (!round.persistSlice(1)) {
        // the anchor carrier must not have landed while data slices remain
        assertThat(anchorState.committedValue(STORE_A, bytes(9))).isNull();
      }
      coordinator.completeRound(round, true);

      // then the anchor carrier and the anchor rode together in the final slice, nothing else
      final List<String> finalSlice =
          recordingSink.committedBatches.get(recordingSink.committedBatches.size() - 1);
      assertThat(finalSlice).containsExactly(putOp(STORE_A, bytes(9), bytes(90)), anchorOp(10));
      assertThat(recordingSink.committedBatches).hasSizeGreaterThan(2);
      assertThat(anchorState.committedValue(STORE_A, bytes(9))).containsExactly(bytes(90));
      assertThat(anchorState.committedValue(STORE_A, bytes(5))).containsExactly(bytes(50));
    }
  }

  @Test
  void shouldNotAdvanceAnchorGaugeWhenRoundCarriedNoAnchorEntry() throws Exception {
    // given a designated-carrier wiring (the durable anchor is a normal drained key — the real
    // wiring's sink anchor cell is a no-op, so recovery reads the carrier, not the cell) with
    // micrometer-backed gauges
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final LayeredStoreMetrics metrics = LayeredStoreMetrics.of(registry, "anchor-gauge");
    final InMemoryDurableState anchorState = new InMemoryDurableState();
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(
            STORE_A, anchorState.store(STORE_A), 1024 * 1024, false, 4, metrics);
    final List<LayeredKeyValueStore> stores = List.of(store);
    try (final LayeredStoreCoordinator coordinator =
        new LayeredStoreCoordinator(
            stores, anchorState.sink(), anchorState.snapshotSource(), view -> {}, metrics)) {
      coordinator.designateAnchorEntry(STORE_A, bytes(9));

      // and a first round that drained the carrier: the durable anchor is 10
      store.put(bytes(9), bytes(90));
      store.promote();
      coordinator.freezeAll(10);
      final PersistRound anchored = coordinator.prepareRound(10);
      anchored.persist();
      coordinator.completeRound(anchored, true);

      // when a round without the carrier drains at watermark 20 — the durable anchor stays 10
      store.put(bytes(1), bytes(10));
      store.promote();
      coordinator.freezeAll(20);
      final PersistRound carrierless = coordinator.prepareRound(20);
      carrierless.persist();
      coordinator.completeRound(carrierless, true);

      // and newer writes freeze at watermark 30
      store.put(bytes(2), bytes(20));
      store.promote();
      coordinator.freezeAll(30);

      // then the anchor-lag gauge measures from the last round that actually committed an
      // anchor (10), never from the carrier-less round's watermark (20) — the gauge must not
      // run ahead of what recovery would read
      assertThat(registry.get("zeebe.db.layered.anchor.lag").gauge().value()).isEqualTo(20.0);
    }
  }

  @Test
  void shouldRejectAnchorEntryDesignationForUnknownStore() {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // when / then
    assertThatThrownBy(() -> coordinator.designateAnchorEntry("no-such-store", bytes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-store");
  }

  @Test
  void shouldConvergeToFullCutWhenSuccessorCompletesPartialDrainForward() throws Exception {
    // given a paced drain that committed partial slices — state ahead of the anchor — before its
    // owner died (a failing slice stands in for the death; the anchor never landed)
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    for (int key = 1; key <= 5; key++) {
      store.put(bytes(key), bytes(10 * key));
    }
    store.promote();
    final PersistRound round = coordinator.prepareRound(10);
    assertThat(round.persistSlice(1)).isFalse();
    assertThat(state.committedSize(STORE_A)).isPositive();
    assertThat(sink.readAnchor()).isEqualTo(-1);
    state.failNextCommit();
    assertThatThrownBy(() -> round.persistSlice(1)).isInstanceOf(IllegalStateException.class);

    // when the successor completes the orphaned round forward
    coordinator.completeOutstandingRoundForward();

    // then the durable store holds the full cut — the re-drain rewrote the partially committed
    // versions idempotently — the anchor landed, and nothing stayed buffered
    assertThat(coordinator.roundOutstanding()).isFalse();
    for (int key = 1; key <= 5; key++) {
      assertThat(state.committedValue(STORE_A, bytes(key))).containsExactly(bytes(10 * key));
    }
    assertThat(sink.readAnchor()).isEqualTo(10);
    assertThat(store.hasBufferedWrites()).isFalse();

    // and completing forward without an outstanding round is a no-op
    coordinator.completeOutstandingRoundForward();
  }

  @Test
  void shouldKeepSegmentsBufferedWhenForwardCompletionFails() throws Exception {
    // given an orphaned round whose forward re-drain fails too
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();
    final PersistRound round = coordinator.prepareRound(10);
    state.failNextCommit();

    // when / then -- the failure is rethrown and the round completed as failed
    assertThatThrownBy(coordinator::completeOutstandingRoundForward)
        .isInstanceOf(IllegalStateException.class);
    assertThat(coordinator.roundOutstanding()).isFalse();
    assertThat(store.hasBufferedWrites()).isTrue();
    assertThat(sink.readAnchor()).isEqualTo(-1);

    // and the successor's next round retries the same segments
    final PersistRound retry = coordinator.prepareRound(11);
    assertThat(retry.anchor()).isEqualTo(round.anchor());
    retry.persist();
    coordinator.completeRound(retry, true);
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(10));
  }

  @Test
  void shouldRejectSliceAfterFullDrain() throws Exception {
    // given a fully drained round
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();
    final PersistRound round = coordinator.prepareRound(10);
    assertThat(round.persistSlice(Long.MAX_VALUE)).isTrue();

    // when / then
    assertThatThrownBy(() -> round.persistSlice(1)).isInstanceOf(IllegalStateException.class);
    coordinator.completeRound(round, true);
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(10));
  }

  @Test
  void shouldNeverPersistValueOfPairAnnihilatedAcrossFreezes() throws Exception {
    // given -- a put frozen at 10 and its delete frozen at 20, so both segments exist
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);
    store.delete(bytes(1));
    store.promote();
    coordinator.freezeAll(20);

    // when
    final PersistRound round = coordinator.prepareRound(20);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- the merged drain collapses the pair, so the value never lands
    assertThat(state.committedValue(STORE_A, bytes(1))).isNull();
    assertThat(state.committedSize(STORE_A)).isZero();
    assertThat(sink.puts).isZero();
    assertThat(sink.readAnchor()).isEqualTo(20);
  }

  @Test
  void shouldSkipNeverFlushedTombstoneEntirelyAtDrain() throws Exception {
    // given -- put and delete in the same batch: only a never-flushed tombstone reaches the segment
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.put(bytes(1), bytes(10));
    store.delete(bytes(1));
    store.promote();
    coordinator.freezeAll(10);

    // when
    final PersistRound round = coordinator.prepareRound(10);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- neither a put nor a delete reaches the delegate; only the anchor advances
    assertThat(sink.puts).isZero();
    assertThat(sink.deletes).isZero();
    assertThat(sink.batchesCreated).isEqualTo(1);
    assertThat(state.committedSize(STORE_A)).isZero();
    assertThat(sink.readAnchor()).isEqualTo(10);
  }

  @Test
  void shouldDeleteDelegateOnlyKeyAfterBlindDelete() throws Exception {
    // given -- the key exists only in the delegate (e.g. persisted by an earlier incarnation)
    // and is deleted without ever being read through this store
    state.store(STORE_A).put(bytes(1), bytes(10));
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    store.delete(bytes(1));
    store.promote();

    // when
    final PersistRound round = coordinator.prepareRound(10);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- the tombstone reached the delegate; the durable row must not resurface in scans
    assertThat(sink.deletes).isEqualTo(1);
    assertThat(state.committedValue(STORE_A, bytes(1))).isNull();
    assertThat(state.committedSize(STORE_A)).isZero();
    assertThat(coordinator.currentView().exists(STORE_A, bytes(1))).isFalse();
  }

  @Test
  void shouldSkipBatchEntirelyWhenRoundCapturedNothing() throws Exception {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // when -- a round over empty pipelines and empty actives
    final PersistRound round = coordinator.prepareRound(5);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- no batch was written and the anchor did not advance
    assertThat(round.anchor()).isEqualTo(-1);
    assertThat(sink.batchesCreated).isZero();
    assertThat(sink.readAnchor()).isEqualTo(-1);
  }

  @Test
  void shouldRejectSecondPrepareWhileRoundOutstanding() {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));
    coordinator.prepareRound(5);

    // when / then
    assertThat(coordinator.roundOutstanding()).isTrue();
    assertThatThrownBy(() -> coordinator.prepareRound(6)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectCompletingWithoutOrWithStaleRound() throws Exception {
    // given
    final LayeredStoreCoordinator coordinator = newCoordinator(newStore(STORE_A));

    // when / then -- no round outstanding
    assertThatThrownBy(() -> coordinator.completeRound(null, true))
        .isInstanceOf(IllegalStateException.class);

    // given -- a completed round and a fresh outstanding one
    final PersistRound completed = coordinator.prepareRound(5);
    completed.persist();
    coordinator.completeRound(completed, true);
    final PersistRound outstanding = coordinator.prepareRound(6);

    // when / then -- the stale round is rejected, the outstanding one still completes
    assertThatThrownBy(() -> coordinator.completeRound(completed, true))
        .isInstanceOf(IllegalStateException.class);
    coordinator.completeRound(outstanding, true);
    assertThat(coordinator.roundOutstanding()).isFalse();
  }

  @Test
  void shouldCommitMultiStoreRoundInOneAtomicBatch() throws Exception {
    // given
    final LayeredKeyValueStore storeA = newStore(STORE_A);
    final LayeredKeyValueStore storeB = newStore(STORE_B);
    final LayeredStoreCoordinator coordinator = newCoordinator(storeA, storeB);
    storeA.put(bytes(1), bytes(10));
    storeA.promote();
    storeB.put(bytes(2), bytes(20));
    storeB.promote();
    coordinator.freezeAll(10);

    // when
    final PersistRound round = coordinator.prepareRound(10);
    round.persist();
    coordinator.completeRound(round, true);

    // then -- both stores' entries plus the anchor landed through a single batch
    assertThat(sink.batchesCreated).isEqualTo(1);
    assertThat(state.committedValue(STORE_A, bytes(1))).containsExactly(bytes(10));
    assertThat(state.committedValue(STORE_B, bytes(2))).containsExactly(bytes(20));
    assertThat(sink.readAnchor()).isEqualTo(10);
  }

  @Test
  void shouldNotifyViewListenerInPublicationOrder() throws Exception {
    // given
    final LayeredKeyValueStore store = newStore(STORE_A);
    final LayeredStoreCoordinator coordinator = newCoordinator(store);
    final ReadOnlyView initial = coordinator.currentView();
    assertThat(views).containsExactly(initial);

    // when -- a freeze, then a full round (whose O(1) prepare publishes nothing: capturing moves
    // nothing a view could already see, and barriers self-freshen through freezeAll)
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);
    final ReadOnlyView afterFreeze = coordinator.currentView();
    final PersistRound round = coordinator.prepareRound(20);
    assertThat(coordinator.currentView()).isSameAs(afterFreeze);
    round.persist();
    coordinator.completeRound(round, true);
    final ReadOnlyView afterComplete = coordinator.currentView();

    // then
    assertThat(views).containsExactly(initial, afterFreeze, afterComplete);
    assertThat(views.get(views.size() - 1)).isSameAs(coordinator.currentView());

    // when -- a failed round publishes nothing at all
    store.put(bytes(2), bytes(20));
    store.promote();
    state.failNextCommit();
    final PersistRound failing = coordinator.prepareRound(30);
    final int publishedAfterPrepare = views.size();
    assertThatThrownBy(failing::persist).isInstanceOf(IllegalStateException.class);
    coordinator.completeRound(failing, false);

    // then
    assertThat(views).hasSize(publishedAfterPrepare);
  }

  /**
   * Parity property: the streamed drain must produce byte-identical batch operations — same puts,
   * deletes and skips, in the same order — as the previous implementation, which materialized
   * {@code FlatSegment.merge(oldestFirst, false)} per store and then streamed the merged segment.
   * The old path is implemented locally below as the oracle, run over the exact segment stacks the
   * round captures. Randomized workloads (fixed seed) mix pre-committed delegate keys, read-through
   * reads (which mark later writes flushed), blind deletes, overwrites and multiple freezes, so the
   * stacks carry shadowed versions with every flushed/tombstone combination the store can produce.
   */
  @Test
  void shouldStreamDrainIdenticallyToMaterializedMergeOracle() throws Exception {
    final Random random = new Random(42);

    for (int trial = 0; trial < 50; trial++) {
      runDrainParityTrial(trial, random, false);
    }
  }

  /**
   * Sliced-drain parity: splitting the same drain stream into paced sub-batch slices (each its own
   * committed batch) must yield the identical operation sequence overall, with the anchor riding
   * alone in the final slice — slicing only moves the commit boundaries, never an operation.
   */
  @Test
  void shouldDrainInSlicesIdenticallyToSingleBatchOracle() throws Exception {
    final Random random = new Random(4242);

    for (int trial = 0; trial < 50; trial++) {
      runDrainParityTrial(trial, random, true);
    }
  }

  private static void runDrainParityTrial(
      final int trial, final Random random, final boolean sliced) throws Exception {
    // given -- a fresh durability unit with pre-committed state and a randomized workload
    final InMemoryDurableState trialState = new InMemoryDurableState();
    final RecordingSink recordingSink = new RecordingSink(trialState.sink());
    final Map<String, LayeredKeyValueStore> stores = new LinkedHashMap<>();
    for (final String name : List.of(STORE_A, STORE_B)) {
      stores.put(name, seededStore(name, trialState, random));
    }
    try (final LayeredStoreCoordinator coordinator =
        new LayeredStoreCoordinator(
            stores.values(),
            recordingSink,
            trialState.snapshotSource(),
            view -> {},
            LayeredStoreMetrics.noop())) {
      long watermark = 0;
      for (int freeze = 1 + random.nextInt(4); freeze > 0; freeze--) {
        for (final LayeredKeyValueStore store : stores.values()) {
          promoteRandomBatch(store, random);
        }
        coordinator.freezeAll(++watermark);
      }

      // given -- the exact segment stacks the round will capture (actives are already frozen,
      // so the capture below carries no tip and consists of precisely these segments)
      final Map<String, List<FlatSegment>> capturedOldestFirst = new LinkedHashMap<>();
      stores.forEach(
          (name, store) -> {
            final List<FlatSegment> oldestFirst = new ArrayList<>(store.segmentsNewestFirst());
            Collections.reverse(oldestFirst);
            capturedOldestFirst.put(name, oldestFirst);
          });
      final List<String> expectedOps = materializedDrainOracle(capturedOldestFirst);

      // when
      final PersistRound round = coordinator.prepareRound(watermark);
      if (sliced) {
        final long minSliceBytes = 1 + random.nextInt(64);
        while (!round.persistSlice(minSliceBytes)) {
          // keep slicing until the anchor-carrying final slice committed
        }
      } else {
        round.persist();
      }
      coordinator.completeRound(round, true);

      // then -- identical operations in identical order, slicing or not
      assertThat(recordingSink.committedOperations())
          .as("batch operations of trial %d", trial)
          .containsExactlyElementsOf(expectedOps);
      if (sliced && !expectedOps.isEmpty()) {
        // and the anchor rode only in the final slice — data slices never carry it, so a crash
        // between slices can only leave state ahead of the anchor, never the anchor ahead of state
        final int batches = recordingSink.committedBatches.size();
        for (int i = 0; i < batches - 1; i++) {
          assertThat(recordingSink.committedBatches.get(i))
              .as("data slice %d of trial %d", i, trial)
              .doesNotContain(anchorOp(round.anchor()));
        }
        assertThat(recordingSink.committedBatches.get(batches - 1))
            .as("final slice of trial %d", trial)
            .endsWith(anchorOp(round.anchor()));
      }
    }
  }

  /** Pre-commits a random handful of keys to the delegate, then opens the store over it. */
  private static LayeredKeyValueStore seededStore(
      final String name, final InMemoryDurableState trialState, final Random random) {
    for (int i = random.nextInt(4); i > 0; i--) {
      trialState.store(name).put(bytes(random.nextInt(8)), bytes(random.nextInt(256)));
    }
    return new LayeredKeyValueStore(name, trialState.store(name), 1024 * 1024, false, 2);
  }

  /** One randomized processing batch: mixed reads, blind deletes and overwrites, then promote. */
  private static void promoteRandomBatch(final LayeredKeyValueStore store, final Random random) {
    for (int op = random.nextInt(7); op > 0; op--) {
      final byte[] key = bytes(random.nextInt(8));
      switch (random.nextInt(4)) {
        case 0 -> store.delete(key); // blind deletes probe the delegate for flushed
        case 1 -> store.get(key); // read-through marks later writes of the key flushed
        default -> store.put(key, bytes(random.nextInt(256)));
      }
    }
    store.promote();
  }

  /** The old drain path — materialize the merged segment per store, then stream it. */
  private static List<String> materializedDrainOracle(
      final Map<String, List<FlatSegment>> capturedOldestFirst) {
    final List<String> operations = new ArrayList<>();
    long anchor = -1;
    for (final Map.Entry<String, List<FlatSegment>> captured : capturedOldestFirst.entrySet()) {
      final List<FlatSegment> oldestFirst = captured.getValue();
      for (final FlatSegment segment : oldestFirst) {
        anchor = Math.max(anchor, segment.watermark());
      }
      if (oldestFirst.isEmpty()) {
        continue;
      }
      final FlatSegment merged = FlatSegment.merge(oldestFirst, false);
      final Iterator<Entry> entries = merged.range(new byte[0]);
      while (entries.hasNext()) {
        final Entry entry = entries.next();
        if (!entry.tombstone()) {
          operations.add(putOp(captured.getKey(), entry.key(), entry.value()));
        } else if (entry.flushed()) {
          operations.add(deleteOp(captured.getKey(), entry.key()));
        }
        // a never-flushed tombstone is skipped: it must not appear in the batch at all
      }
    }
    if (!operations.isEmpty() || anchor >= 0) {
      operations.add(anchorOp(anchor));
    }
    return operations;
  }

  private static String putOp(final String storeName, final byte[] key, final byte[] value) {
    return "put " + storeName + " " + Arrays.toString(key) + " -> " + Arrays.toString(value);
  }

  private static String deleteOp(final String storeName, final byte[] key) {
    return "delete " + storeName + " " + Arrays.toString(key);
  }

  private static String anchorOp(final long position) {
    return "anchor " + position;
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  /**
   * Records every operation of every committed batch, in order, on its way to the real sink — for
   * parity assertions over the flattened stream and slice assertions over the batch boundaries.
   */
  private static final class RecordingSink implements PersistSink {

    private final PersistSink delegate;
    private final List<List<String>> committedBatches = new ArrayList<>();

    RecordingSink(final PersistSink delegate) {
      this.delegate = delegate;
    }

    private List<String> committedOperations() {
      final List<String> flattened = new ArrayList<>();
      committedBatches.forEach(flattened::addAll);
      return flattened;
    }

    @Override
    public PersistBatch newBatch() {
      final PersistBatch batch = delegate.newBatch();
      final List<String> operations = new ArrayList<>();
      return new PersistBatch() {
        @Override
        public void put(final String storeName, final byte[] key, final byte[] value) {
          operations.add(putOp(storeName, key, value));
          batch.put(storeName, key, value);
        }

        @Override
        public void delete(final String storeName, final byte[] key) {
          operations.add(deleteOp(storeName, key));
          batch.delete(storeName, key);
        }

        @Override
        public void putAnchor(final long position) {
          operations.add(anchorOp(position));
          batch.putAnchor(position);
        }

        @Override
        public void commit() throws Exception {
          batch.commit();
          committedBatches.add(operations);
        }

        @Override
        public void close() {
          batch.close();
        }
      };
    }

    @Override
    public long readAnchor() {
      return delegate.readAnchor();
    }
  }

  /** Counts batches and writes on their way to the real sink, for annihilation assertions. */
  private static final class CountingSink implements PersistSink {

    private final PersistSink delegate;
    private int batchesCreated;
    private int puts;
    private int deletes;

    CountingSink(final PersistSink delegate) {
      this.delegate = delegate;
    }

    @Override
    public PersistBatch newBatch() {
      batchesCreated++;
      final PersistBatch batch = delegate.newBatch();
      return new PersistBatch() {
        @Override
        public void put(final String storeName, final byte[] key, final byte[] value) {
          puts++;
          batch.put(storeName, key, value);
        }

        @Override
        public void delete(final String storeName, final byte[] key) {
          deletes++;
          batch.delete(storeName, key);
        }

        @Override
        public void putAnchor(final long position) {
          batch.putAnchor(position);
        }

        @Override
        public void commit() throws Exception {
          batch.commit();
        }

        @Override
        public void close() {
          batch.close();
        }
      };
    }

    @Override
    public long readAnchor() {
      return delegate.readAnchor();
    }
  }
}
