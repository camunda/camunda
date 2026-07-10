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

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.ArrayList;
import java.util.List;
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
    return new LayeredStoreCoordinator(List.of(stores), sink, state.snapshotSource(), views::add);
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

    // when -- a freeze, then a full round (whose prepare freezes again)
    store.put(bytes(1), bytes(10));
    store.promote();
    coordinator.freezeAll(10);
    final ReadOnlyView afterFreeze = coordinator.currentView();
    final PersistRound round = coordinator.prepareRound(20);
    final ReadOnlyView afterPrepare = coordinator.currentView();
    round.persist();
    coordinator.completeRound(round, true);
    final ReadOnlyView afterComplete = coordinator.currentView();

    // then
    assertThat(views).containsExactly(initial, afterFreeze, afterPrepare, afterComplete);
    assertThat(views.get(views.size() - 1)).isSameAs(coordinator.currentView());

    // when -- a failed round publishes nothing beyond its own prepare-freeze
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

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
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
