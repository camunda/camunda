/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.segment.FlatSegment;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Freeze/persist lifecycle, delete absorption and byte-budget behavior. The store never writes the
 * delegate itself — the coordinator does — so tests simulate a persist round by draining the
 * segments returned from {@link LayeredKeyValueStore#beginPersist()} into the delegate manually.
 */
final class LayeredKeyValueStoreLifecycleTest {

  private static final String STORE = "cf";

  private final InMemoryDurableState state = new InMemoryDurableState();

  private LayeredKeyValueStore newStore(
      final long maxBytes, final boolean absorbDeletes, final int pipelineSegmentLimit) {
    return new LayeredKeyValueStore(
        STORE, state.store(STORE), maxBytes, absorbDeletes, pipelineSegmentLimit);
  }

  private LayeredKeyValueStore newStore() {
    return newStore(1024 * 1024, false, 10);
  }

  // ------------------------------------------------------------------
  // freeze
  // ------------------------------------------------------------------

  @Test
  void shouldStampWatermarkOnFreeze() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("value"));
    store.promote();

    // when
    store.freeze(42L);

    // then
    assertThat(store.segmentsNewestFirst()).hasSize(1);
    assertThat(store.segmentsNewestFirst().get(0).watermark()).isEqualTo(42L);
  }

  @Test
  void shouldRejectFreezeWhileBatchInFlight() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("value"));

    // when / then
    assertThatThrownBy(() -> store.freeze(1L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldSkipFreezeWithEmptyActiveOverlay() {
    // given
    final LayeredKeyValueStore store = newStore();

    // when
    store.freeze(1L);

    // then
    assertThat(store.segmentsNewestFirst()).isEmpty();
  }

  @Test
  void shouldMergePipelineBeyondSegmentLimit() {
    // given
    final LayeredKeyValueStore store = newStore(1024 * 1024, false, 2);
    putPromoteFreeze(store, "key1", "v1", 1L);
    putPromoteFreeze(store, "key2", "v2", 2L);

    // when -- the third freeze pushes the pipeline over the limit
    putPromoteFreeze(store, "key3", "v3", 3L);

    // then -- merged down to one segment carrying the newest watermark and all entries
    final List<FlatSegment> segments = store.segmentsNewestFirst();
    assertThat(segments).hasSize(1);
    assertThat(segments.get(0).watermark()).isEqualTo(3L);
    assertThat(segments.get(0).entryCount()).isEqualTo(3);
    assertThat(store.get(bytes("key1"))).isEqualTo(bytes("v1"));
    assertThat(store.get(bytes("key3"))).isEqualTo(bytes("v3"));
  }

  @Test
  void shouldMergeOnlyNonPersistingSegments() {
    // given -- two segments handed to an outstanding persist round
    final LayeredKeyValueStore store = newStore(1024 * 1024, false, 2);
    putPromoteFreeze(store, "old1", "v", 1L);
    putPromoteFreeze(store, "old2", "v", 2L);
    store.beginPersist();

    // when -- three more freezes exceed the limit for the non-persisting run
    putPromoteFreeze(store, "new1", "v", 3L);
    putPromoteFreeze(store, "new2", "v", 4L);
    putPromoteFreeze(store, "new3", "v", 5L);

    // then -- only the non-persisting segments merged; the persisting ones are untouched
    final List<FlatSegment> segments = store.segmentsNewestFirst();
    assertThat(segments).hasSize(3);
    assertThat(segments.get(0).watermark()).isEqualTo(5L);
    assertThat(segments.get(0).entryCount()).isEqualTo(3);
    assertThat(segments.get(1).watermark()).isEqualTo(2L);
    assertThat(segments.get(2).watermark()).isEqualTo(1L);
  }

  @Test
  void shouldSkipMergeAfterLowAnnihilationMergeUntilHardCap() {
    // given -- limit 2; the first over-limit merge of fully disjoint segments measures zero
    // annihilation (3 entries in, 3 out)
    final var registry = new SimpleMeterRegistry();
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(
            STORE,
            state.store(STORE),
            1024 * 1024,
            false,
            2,
            LayeredStoreMetrics.of(registry, "t"));
    putPromoteFreeze(store, "k1", "v", 1L);
    putPromoteFreeze(store, "k2", "v", 2L);
    putPromoteFreeze(store, "k3", "v", 3L);
    assertThat(store.pipelineDepth()).isEqualTo(1);

    // when -- further disjoint freezes exceed the limit again
    putPromoteFreeze(store, "k4", "v", 4L);
    putPromoteFreeze(store, "k5", "v", 5L);

    // then -- the merge is skipped (and metered): the pipeline overshoots past the limit
    assertThat(store.pipelineDepth()).isEqualTo(3);
    assertThat(skippedMerges(registry)).isEqualTo(1);

    // when -- the overshoot reaches the hard cap (2x the limit)
    putPromoteFreeze(store, "k6", "v", 6L);

    // then -- the merge is forced, collapsing everything back to one readable segment
    assertThat(store.pipelineDepth()).isEqualTo(1);
    assertThat(store.get(bytes("k1"))).isEqualTo(bytes("v"));
    assertThat(store.get(bytes("k6"))).isEqualTo(bytes("v"));
  }

  @Test
  void shouldKeepMergingAtLimitWhileAnnihilationStaysHigh() {
    // given -- limit 2; every segment overwrites the same key, so merges fully deduplicate
    final var registry = new SimpleMeterRegistry();
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(
            STORE,
            state.store(STORE),
            1024 * 1024,
            false,
            2,
            LayeredStoreMetrics.of(registry, "t"));
    putPromoteFreeze(store, "key", "v1", 1L);
    putPromoteFreeze(store, "key", "v2", 2L);
    putPromoteFreeze(store, "key", "v3", 3L);
    assertThat(store.pipelineDepth()).isEqualTo(1);

    // when -- the next freezes exceed the limit again
    putPromoteFreeze(store, "key", "v4", 4L);
    putPromoteFreeze(store, "key", "v5", 5L);

    // then -- merged promptly at the limit (the measured ratio stays high), no overshoot
    assertThat(store.pipelineDepth()).isEqualTo(1);
    assertThat(skippedMerges(registry)).isZero();
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("v5"));
  }

  private static double skippedMerges(final SimpleMeterRegistry registry) {
    return registry.get("zeebe.db.layered.pipeline.merges.skipped").counter().count();
  }

  // ------------------------------------------------------------------
  // persist lifecycle
  // ------------------------------------------------------------------

  @Test
  void shouldReturnSegmentsOldestFirstFromBeginPersist() {
    // given
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key1", "v1", 1L);
    putPromoteFreeze(store, "key2", "v2", 2L);

    // when
    final List<FlatSegment> draining = store.beginPersist();

    // then
    assertThat(draining).hasSize(2);
    assertThat(draining.get(0).watermark()).isEqualTo(1L);
    assertThat(draining.get(1).watermark()).isEqualTo(2L);
  }

  @Test
  void shouldRejectConcurrentPersistRounds() {
    // given
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key", "value", 1L);
    store.beginPersist();

    // when / then
    assertThatThrownBy(store::beginPersist).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectCompletePersistWithoutOutstandingRound() {
    // given
    final LayeredKeyValueStore store = newStore();

    // when / then
    assertThatThrownBy(() -> store.completePersist(true)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldDropSegmentsOnSuccessfulPersist() {
    // given
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key", "value", 1L);
    final List<FlatSegment> draining = store.beginPersist();
    drainToDelegate(draining);

    // when
    store.completePersist(true);

    // then -- pipeline empty, delegate authoritative, value still readable
    assertThat(store.segmentsNewestFirst()).isEmpty();
    assertThat(state.committedValue(STORE, bytes("key"))).isEqualTo(bytes("value"));
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));
  }

  @Test
  void shouldDropPersistedSegmentsWithoutPopulatingCleanCache() {
    // given
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key", "value", 1L);
    drainToDelegate(store.beginPersist());

    // when
    store.completePersist(true);

    // then -- retirement is lazy: no owner-thread burst copies drained values into the clean
    // cache; nothing stays buffered in any layer
    assertThat(store.cleanEntryCount()).isZero();
    assertThat(store.approximateBytes()).isZero();
  }

  @Test
  void shouldRepopulateCleanCacheLazilyAfterPersist() {
    // given -- a fully persisted key; lazy retirement leaves the clean cache empty
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE, counting, 1024 * 1024, false, 10);
    putPromoteFreeze(store, "key", "value", 1L);
    drainToDelegate(store.beginPersist());
    store.completePersist(true);
    final int getsAfterPersist = counting.gets;

    // when -- the first read falls through to the delegate and caches the value
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));

    // then -- exactly one probe; the second read is served by the clean cache
    assertThat(counting.gets).isEqualTo(getsAfterPersist + 1);
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));
    assertThat(counting.gets).isEqualTo(getsAfterPersist + 1);
  }

  @Test
  void shouldInsertFreshKeyAfterExistsCheckWithSingleDelegateProbe() {
    // given -- the common check-then-insert pattern on a fresh key
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE, counting, 1024 * 1024, false, 10);
    assertThat(store.exists(bytes("key"))).isFalse();

    // when -- the insert's flushed check is answered by the cached negative, not the delegate
    store.put(bytes("key"), bytes("value"));
    store.promote();
    store.freeze(1L);

    // then -- one delegate probe total, and the insert carries exactly flushed=false
    assertThat(counting.gets).isEqualTo(1);
    final Entry entry = store.segmentsNewestFirst().get(0).findEntry(bytes("key"));
    assertThat(entry).isNotNull();
    assertThat(entry.tombstone()).isFalse();
    assertThat(entry.flushed()).isFalse();
  }

  @Test
  void shouldServePersistedValueAfterEarlierCachedNegative() {
    // given -- a read miss cached a negative, then the key is written and fully persisted
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE, counting, 1024 * 1024, false, 10);
    assertThat(store.get(bytes("key"))).isNull();
    putPromoteFreeze(store, "key", "value", 1L);
    drainToDelegate(store.beginPersist());

    // when -- the segments drop; the write already removed the stale negative at write time
    store.completePersist(true);

    // then -- the persisted value is served (no stale negative) via one lazy read-through
    final int getsAfterPersist = counting.gets;
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("value"));
    assertThat(counting.gets).isEqualTo(getsAfterPersist + 1);
  }

  @Test
  void shouldKeepSegmentsOnFailedPersistAndRetryThem() {
    // given
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key1", "v1", 1L);
    putPromoteFreeze(store, "key2", "v2", 2L);
    store.beginPersist();

    // when -- the round fails without draining anything
    store.completePersist(false);

    // then -- segments stay readable and a retry returns them again, oldest first
    assertThat(store.segmentsNewestFirst()).hasSize(2);
    assertThat(store.get(bytes("key1"))).isEqualTo(bytes("v1"));
    assertThat(state.committedSize(STORE)).isZero();
    final List<FlatSegment> retry = store.beginPersist();
    assertThat(retry).hasSize(2);
    assertThat(retry.get(0).watermark()).isEqualTo(1L);
    assertThat(retry.get(1).watermark()).isEqualTo(2L);
  }

  @Test
  void shouldDropPersistedValueShadowedByNewerVersion() {
    // given -- a newer version promoted while the persist round is outstanding
    final LayeredKeyValueStore store = newStore();
    putPromoteFreeze(store, "key", "old", 1L);
    final List<FlatSegment> draining = store.beginPersist();
    store.put(bytes("key"), bytes("new"));
    store.promote();
    drainToDelegate(draining);

    // when
    store.completePersist(true);

    // then -- the newer version stays authoritative over the just-persisted one
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("new"));
    assertThat(state.committedValue(STORE, bytes("key"))).isEqualTo(bytes("old"));
  }

  @Test
  void shouldPersistTombstoneOfFlushedKeyAfterPersistedPutWasSuperseded() {
    // given -- key reaches the delegate, then a newer version and finally a delete
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);
    putPromoteFreeze(store, "key", "old", 1L);
    drainToDelegate(store.beginPersist());
    store.completePersist(true);
    store.put(bytes("key"), bytes("new"));
    store.promote();
    store.delete(bytes("key"));
    store.promote();
    store.freeze(2L);

    // when -- despite delete absorption the tombstone must survive: the delegate holds the key
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // then
    assertThat(state.committedValue(STORE, bytes("key"))).isNull();
    assertThat(store.get(bytes("key"))).isNull();
  }

  // ------------------------------------------------------------------
  // delete absorption
  // ------------------------------------------------------------------

  @Test
  void shouldAnnihilatePutDeletePairInSameBatch() {
    // given
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);
    store.put(bytes("key"), bytes("value"));
    store.delete(bytes("key"));

    // when
    store.promote();

    // then -- the pair annihilated: nothing buffered anywhere, nothing to freeze or persist
    // (bytes checked before the read: the read itself caches a negative for the absent key)
    assertThat(store.approximateBytes()).isZero();
    assertThat(store.get(bytes("key"))).isNull();
    store.freeze(1L);
    assertThat(store.segmentsNewestFirst()).isEmpty();
    assertThat(store.beginPersist()).isEmpty();
    store.completePersist(true);
    assertThat(state.committedSize(STORE)).isZero();
  }

  @Test
  void shouldAnnihilateDeleteOfNeverFlushedActivePut() {
    // given -- put and delete in separate batches, neither ever flushed
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);
    store.put(bytes("key"), bytes("value"));
    store.promote();
    store.delete(bytes("key"));

    // when
    store.promote();

    // then -- bytes checked before the read: the read itself caches a negative for the absent key
    assertThat(store.approximateBytes()).isZero();
    assertThat(store.get(bytes("key"))).isNull();
    store.freeze(1L);
    assertThat(store.segmentsNewestFirst()).isEmpty();
    assertThat(state.committedSize(STORE)).isZero();
  }

  @Test
  void shouldAnnihilateDeleteAfterCachedNegativeWithoutSecondProbe() {
    // given -- an absorb-deletes store; a read miss cached a negative for the key
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE, counting, 1024 * 1024, true, 10);
    assertThat(store.get(bytes("key"))).isNull();

    // when -- the delete's flushed check is answered by the negative (exactly flushed=false), so
    // the unflushed tombstone annihilates on promote without a second delegate probe
    store.delete(bytes("key"));
    store.promote();

    // then -- only the initial probe was paid; nothing buffered, nothing reaches the delegate
    assertThat(counting.gets).isEqualTo(1);
    assertThat(store.approximateBytes()).isZero();
    store.freeze(1L);
    assertThat(store.segmentsNewestFirst()).isEmpty();
    assertThat(state.committedSize(STORE)).isZero();
  }

  @Test
  void shouldKeepTombstoneOfFlushedKeyThroughPromoteAndFreeze() {
    // given -- the key reached the delegate and sits in the clean cache
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);
    putPromoteFreeze(store, "key", "value", 1L);
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // when -- deleting a flushed key must produce a durable tombstone, not an absorption
    store.delete(bytes("key"));
    store.promote();
    store.freeze(2L);

    // then -- the tombstone survived into the frozen segment, marked flushed
    final List<FlatSegment> segments = store.segmentsNewestFirst();
    assertThat(segments).hasSize(1);
    final Entry tombstone = segments.get(0).findEntry(bytes("key"));
    assertThat(tombstone).isNotNull();
    assertThat(tombstone.tombstone()).isTrue();
    assertThat(tombstone.flushed()).isTrue();

    // when -- the next round drains it
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // then
    assertThat(state.committedValue(STORE, bytes("key"))).isNull();
    assertThat(store.get(bytes("key"))).isNull();
  }

  @Test
  void shouldMarkBlindDeleteOfDelegateOnlyKeyFlushed() {
    // given -- the key exists only in the delegate; the store never read it
    state.store(STORE).put(bytes("key"), bytes("value"));
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);

    // when -- a blind delete, no prior get/exists/scan
    store.delete(bytes("key"));
    store.promote();
    store.freeze(1L);

    // then -- the tombstone is flushed, so a persist round must emit the delete instead of
    // absorbing it and resurrecting the durable row
    final Entry tombstone = store.segmentsNewestFirst().get(0).findEntry(bytes("key"));
    assertThat(tombstone).isNotNull();
    assertThat(tombstone.tombstone()).isTrue();
    assertThat(tombstone.flushed()).isTrue();

    // when
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // then
    assertThat(state.committedValue(STORE, bytes("key"))).isNull();
    assertThat(store.get(bytes("key"))).isNull();
  }

  @Test
  void shouldNotAbsorbDeleteOfBlindPutOverDelegateHeldKey() {
    // given -- the delegate holds an old version; the store blindly overwrites then deletes
    state.store(STORE).put(bytes("key"), bytes("old"));
    final LayeredKeyValueStore store = newStore(1024 * 1024, true, 10);
    store.put(bytes("key"), bytes("new"));
    store.delete(bytes("key"));
    store.promote();
    store.freeze(1L);

    // then -- the pair must not annihilate: the delegate provably holds the key
    final Entry tombstone = store.segmentsNewestFirst().get(0).findEntry(bytes("key"));
    assertThat(tombstone).isNotNull();
    assertThat(tombstone.tombstone()).isTrue();
    assertThat(tombstone.flushed()).isTrue();

    // when
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // then -- the old version is gone, not resurrected
    assertThat(state.committedValue(STORE, bytes("key"))).isNull();
    assertThat(store.get(bytes("key"))).isNull();
  }

  // ------------------------------------------------------------------
  // byte budget
  // ------------------------------------------------------------------

  @Test
  void shouldTrackApproximateBytesAcrossLayers() {
    // given
    final LayeredKeyValueStore store = newStore();
    final long entryBytes = "key".length() + "value".length();

    // when / then -- the same bytes travel staging -> active -> pipeline
    store.put(bytes("key"), bytes("value"));
    assertThat(store.approximateBytes()).isEqualTo(entryBytes);
    store.promote();
    assertThat(store.approximateBytes()).isEqualTo(entryBytes);
    store.freeze(1L);
    assertThat(store.approximateBytes()).isEqualTo(entryBytes);
  }

  @Test
  void shouldResetStagingBytesOnDiscard() {
    // given
    final LayeredKeyValueStore store = newStore();
    store.put(bytes("key"), bytes("value"));

    // when
    store.discard();

    // then
    assertThat(store.approximateBytes()).isZero();
  }

  @Test
  void shouldEvictCleanEntriesOnReadThroughOverflow() {
    // given -- budget for two clean entries; three delegate keys
    final byte[] value = bytes("0123456789abcdef"); // 16 bytes; entry = 18 bytes
    state.store(STORE).put(bytes("k1"), value);
    state.store(STORE).put(bytes("k2"), value);
    state.store(STORE).put(bytes("k3"), value);
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store = new LayeredKeyValueStore(STORE, counting, 40, false, 10);

    // when -- the third read-through overflows the budget
    store.get(bytes("k1"));
    store.get(bytes("k2"));
    store.get(bytes("k3"));

    // then -- eldest (k1) evicted, k3 still cached
    assertThat(store.approximateBytes()).isLessThanOrEqualTo(40);
    assertThat(counting.gets).isEqualTo(3);
    assertThat(store.get(bytes("k3"))).isEqualTo(value);
    assertThat(counting.gets).isEqualTo(3);
    assertThat(store.get(bytes("k1"))).isEqualTo(value);
    assertThat(counting.gets).isEqualTo(4);
  }

  @Test
  void shouldReprobeDelegateAfterCachedNegativeIsEvicted() {
    // given -- a cached negative (key bytes only), then positive read-throughs overflow the budget
    final byte[] value = bytes("0123456789abcdef"); // 16 bytes; entry = 18 bytes
    state.store(STORE).put(bytes("k1"), value);
    state.store(STORE).put(bytes("k2"), value);
    final CountingBytesStore counting = new CountingBytesStore(state.store(STORE));
    final LayeredKeyValueStore store = new LayeredKeyValueStore(STORE, counting, 36, false, 10);
    assertThat(store.get(bytes("gone"))).isNull(); // negative cached, 4 bytes

    // when -- the second positive entry evicts the eldest clean entry, the negative
    store.get(bytes("k1"));
    store.get(bytes("k2"));

    // then -- eviction only costs a re-probe: the next read of the absent key asks the delegate
    assertThat(store.approximateBytes()).isLessThanOrEqualTo(36);
    assertThat(counting.gets).isEqualTo(3);
    assertThat(store.get(bytes("gone"))).isNull();
    assertThat(counting.gets).isEqualTo(4);
  }

  @Test
  void shouldSignalOverCapacityWhilePinnedBytesExceedBudget() {
    // given -- one pinned entry larger than the whole budget
    final LayeredKeyValueStore store = newStore(10, false, 10);
    store.put(bytes("key"), bytes("a-value-well-over-budget"));
    assertThat(store.overCapacity()).isTrue();
    store.promote();
    store.freeze(1L);
    assertThat(store.overCapacity()).isTrue();

    // when -- a persist round drains the pinned bytes
    drainToDelegate(store.beginPersist());
    store.completePersist(true);

    // then -- the pinned bytes are gone (the drained segments dropped) and reads still resolve
    // through the delegate
    assertThat(store.overCapacity()).isFalse();
    assertThat(store.get(bytes("key"))).isEqualTo(bytes("a-value-well-over-budget"));
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  private void putPromoteFreeze(
      final LayeredKeyValueStore store,
      final String key,
      final String value,
      final long watermark) {
    store.put(bytes(key), bytes(value));
    store.promote();
    store.freeze(watermark);
  }

  /** Simulates the coordinator's drain: newest version per key wins via oldest-first replay. */
  private void drainToDelegate(final List<FlatSegment> oldestFirst) {
    final BytesStore delegate = state.store(STORE);
    for (final FlatSegment segment : oldestFirst) {
      final Iterator<Entry> entries = segment.range(new byte[0]);
      while (entries.hasNext()) {
        final Entry entry = entries.next();
        if (entry.tombstone()) {
          delegate.delete(entry.key());
        } else {
          delegate.put(entry.key(), entry.value());
        }
      }
    }
  }

  private static byte[] bytes(final String value) {
    return value.getBytes(UTF_8);
  }

  /** Counts delegate point reads, to prove which reads the clean cache absorbs. */
  private static final class CountingBytesStore implements BytesStore {

    private final BytesStore delegate;
    private int gets;

    private CountingBytesStore(final BytesStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public byte[] get(final byte[] key) {
      gets++;
      return delegate.get(key);
    }

    @Override
    public void put(final byte[] key, final byte[] value) {
      delegate.put(key, value);
    }

    @Override
    public void delete(final byte[] key) {
      delegate.delete(key);
    }

    @Override
    public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
      delegate.prefixScan(prefix, visitor);
    }
  }
}
