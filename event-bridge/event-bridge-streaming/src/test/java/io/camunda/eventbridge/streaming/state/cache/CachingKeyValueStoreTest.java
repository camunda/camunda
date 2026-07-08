/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CachingKeyValueStoreTest {

  private static final long LARGE_BUDGET = 64L * 1024 * 1024;

  @Test
  void shouldServeReadThroughOnMissAndCacheIt() {
    // given — a value only in the delegate
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "a", 7L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);

    // when — first read falls through, second is served from the cache
    final long first = get(cache, "a");
    final long second = get(cache, "a");

    // then
    assertThat(first).isEqualTo(7L);
    assertThat(second).isEqualTo(7L);
    assertThat(delegate.gets).isEqualTo(1); // second read did not touch the delegate
  }

  @Test
  void shouldReadOwnWritesWithoutTouchingTheDelegate() {
    // given
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);

    // when — a write is buffered, not checkpointed
    put(cache, "a", 7L);

    // then — the cache serves it; the delegate has nothing yet
    assertThat(get(cache, "a")).isEqualTo(7L);
    assertThat(get(delegate, "a")).isEqualTo(-1L);
  }

  @Test
  void shouldFlushPutsAndDeletesOnCheckpoint() {
    // given — the delegate holds a key the cache will delete
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "gone", 1L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);

    // when
    put(cache, "kept", 5L);
    delete(cache, "gone");
    cache.checkpoint();

    // then — the delegate reflects the buffered put and delete
    assertThat(get(delegate, "kept")).isEqualTo(5L);
    assertThat(get(delegate, "gone")).isEqualTo(-1L);
  }

  @Test
  void shouldEvictCleanEntriesUnderMemoryPressureAndReReadThem() {
    // given — a small budget and many delegate entries
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "a", 1L);
    for (int i = 0; i < 200; i++) {
      put(delegate, "k" + i, i);
    }
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);

    // when — read "a" (cached), then read enough other keys to push it out, then read "a" again
    get(cache, "a");
    final int afterFirst = delegate.gets;
    get(cache, "a"); // still cached — no extra delegate read
    final int afterHit = delegate.gets;
    for (int i = 0; i < 200; i++) {
      get(cache, "k" + i);
    }
    get(cache, "a");

    // then — the hit was free, and "a" was evicted and re-read from the delegate afterwards
    assertThat(afterHit).isEqualTo(afterFirst);
    assertThat(delegate.gets).isGreaterThan(afterHit + 200);
  }

  @Test
  void shouldNotEvictDirtyEntriesUnderMemoryPressure() {
    // given — a small budget, many clean delegate entries, and one dirty (unflushed) write
    final CountingDelegate delegate = new CountingDelegate();
    for (int i = 0; i < 200; i++) {
      put(delegate, "k" + i, i);
    }
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);
    put(cache, "pinned", 99L);

    // when — churn far past the budget by reading clean entries
    for (int i = 0; i < 200; i++) {
      get(cache, "k" + i);
    }

    // then — the dirty write survived (served from the cache, still absent from the delegate)
    assertThat(get(cache, "pinned")).isEqualTo(99L);
    assertThat(get(delegate, "pinned")).isEqualTo(-1L);
  }

  @Test
  void shouldMergeDelegateAndDirtyOverlayInKeyOrderOnScan() {
    // given — a checkpointed delegate, then buffered writes: an insert, an override, a delete
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "a", 1L);
    put(delegate, "c", 3L);
    put(delegate, "e", 5L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "b", 2L); // new
    put(cache, "c", 30L); // override
    delete(cache, "e"); // hidden

    // when
    final List<String> seen = new ArrayList<>();
    cache.forEach((k, v) -> seen.add(k.toString() + "=" + v.getValue()));

    // then — delegate and overlay merged in key order, tombstone excluded
    assertThat(seen).containsExactly("a=1", "b=2", "c=30");
  }

  @Test
  void shouldScanTheOverlayRebuiltAfterCheckpoint() {
    // given — a first generation of writes made durable by a checkpoint (the dirty overlay resets)
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "a", 1L);
    put(cache, "c", 3L);
    cache.checkpoint();

    // when — a second generation of buffered writes: an override, an insert, and a delete of a
    // now-clean (checkpointed) entry
    put(cache, "a", 10L);
    put(cache, "b", 2L);
    delete(cache, "c");
    final List<String> seen = new ArrayList<>();
    cache.forEach((k, v) -> seen.add(k.toString() + "=" + v.getValue()));

    // then — the fresh overlay merges over the checkpointed state in key order
    assertThat(seen).containsExactly("a=10", "b=2");

    // when — the second checkpoint flushes the new generation
    cache.checkpoint();

    // then — the delegate converges on the merged view
    assertThat(get(delegate, "a")).isEqualTo(10L);
    assertThat(get(delegate, "b")).isEqualTo(2L);
    assertThat(get(delegate, "c")).isEqualTo(-1L);
  }

  @Test
  void shouldAbsorbAPutThenDeletePairThatNeverReachedTheDelegate() {
    // given — an absorbing cache (deletes are GC: a deleted key is never read again)
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache =
        new CachingKeyValueStore<>(delegate, DbString::new, DbLong::new, LARGE_BUDGET, true);

    // when — a short-lived put+delete pair within one checkpoint interval
    put(cache, "ephemeral", 7L);
    delete(cache, "ephemeral");
    cache.checkpoint();

    // then — the pair annihilated: neither the put nor a tombstone reached the delegate
    assertThat(delegate.deletes).isZero();
    assertThat(get(delegate, "ephemeral")).isEqualTo(-1L);
    assertThat(get(cache, "ephemeral")).isEqualTo(-1L);
  }

  @Test
  void shouldStillTombstoneDelegateBackedKeysWhenAbsorbing() {
    // given — an absorbing cache with one read-through entry and one checkpointed (flushed) put
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "readThrough", 1L);
    final CachingKeyValueStore<DbString, DbLong> cache =
        new CachingKeyValueStore<>(delegate, DbString::new, DbLong::new, LARGE_BUDGET, true);
    get(cache, "readThrough");
    put(cache, "flushed", 2L);
    cache.checkpoint();

    // when — both delegate-backed keys are deleted and the deletes are checkpointed
    delete(cache, "readThrough");
    delete(cache, "flushed");
    cache.checkpoint();

    // then — real tombstones were flushed for both (absorption applies only to never-flushed puts)
    assertThat(delegate.deletes).isEqualTo(2);
    assertThat(get(delegate, "readThrough")).isEqualTo(-1L);
    assertThat(get(delegate, "flushed")).isEqualTo(-1L);
  }

  @Test
  void shouldReportOverCapacityWhileDirtyThenClearAfterCheckpoint() {
    // given — a tiny budget
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);

    // when — buffered (dirty) writes pile up past the budget; being un-flushed, they cannot evict
    for (int i = 0; i < 50; i++) {
      put(cache, "k" + i, i);
    }

    // then — the cache is over capacity, and nothing leaked to the delegate ahead of the commit
    assertThat(cache.overCapacity()).isTrue();
    assertThat(get(delegate, "k0")).isEqualTo(-1L);

    // when — the commit barrier flushes them as one atomic cut
    cache.checkpoint();

    // then — flushed durably, marked clean, and trimmed back under budget
    assertThat(cache.overCapacity()).isFalse();
    assertThat(get(delegate, "k0")).isEqualTo(0L);
    assertThat(get(delegate, "k49")).isEqualTo(49L);
  }

  @Test
  void shouldServeAnAllDirtyOverBudgetCacheAndTrimOnlyAfterCheckpoint() {
    // given — a tiny budget and an all-dirty (pinned) working set far past it
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);
    for (int i = 0; i < 50; i++) {
      put(cache, "k" + i, i);
    }

    // when — repeated puts keep hitting the over-budget, all-pinned cache
    for (int i = 0; i < 50; i++) {
      put(cache, "k" + i, i + 1_000);
    }

    // then — nothing was evicted or leaked: every value is served from the cache without a
    // delegate read, and the delegate holds nothing ahead of the commit
    for (int i = 0; i < 50; i++) {
      assertThat(get(cache, "k" + i)).isEqualTo(i + 1_000L);
    }
    assertThat(delegate.gets).isZero();
    assertThat(cache.overCapacity()).isTrue();

    // when — the commit barrier flushes the working set clean
    cache.checkpoint();

    // then — eviction trims back to budget, and a trimmed entry re-reads its checkpointed value
    // from the delegate
    assertThat(cache.overCapacity()).isFalse();
    assertThat(get(cache, "k0")).isEqualTo(1_000L);
    assertThat(delegate.gets).isGreaterThan(0);
  }

  @Test
  void shouldResolveReadsMutableOverFrozenOverCleanOverDelegate() {
    // given — the same key written in every layer: delegate, clean (read-through), frozen, mutable
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "a", 1L);
    put(delegate, "clean", 5L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    get(cache, "a"); // clean layer now holds a=1
    put(cache, "a", 2L);
    put(cache, "frozenOnly", 20L);
    cache.freeze(); // frozen layer holds a=2 and frozenOnly=20
    put(cache, "a", 3L); // mutable layer holds a=3
    get(cache, "clean"); // clean layer holds clean=5
    final int getsAfterSetup = delegate.gets;

    // when / then — first hit wins top-down, without falling through to the delegate
    assertThat(get(cache, "a")).isEqualTo(3L); // mutable over frozen over clean
    assertThat(get(cache, "frozenOnly")).isEqualTo(20L); // frozen
    assertThat(get(cache, "clean")).isEqualTo(5L); // clean
    assertThat(exists(cache, "a")).isTrue();
    assertThat(exists(cache, "frozenOnly")).isTrue();
    assertThat(delegate.gets).isEqualTo(getsAfterSetup);
    assertThat(exists(cache, "missing")).isFalse(); // falls through all layers
  }

  @Test
  void shouldHideLowerLayersWithATombstoneAtAnyOverlayLevel() {
    // given — a frozen tombstone over a delegate value, and a mutable tombstone over a frozen put
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "frozenDeleted", 1L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    delete(cache, "frozenDeleted");
    put(cache, "mutableDeleted", 2L);
    cache.freeze();
    delete(cache, "mutableDeleted");

    // when / then — both keys are hidden even though a lower layer holds a value
    assertThat(get(cache, "frozenDeleted")).isEqualTo(-1L);
    assertThat(exists(cache, "frozenDeleted")).isFalse();
    assertThat(get(cache, "mutableDeleted")).isEqualTo(-1L);
    assertThat(exists(cache, "mutableDeleted")).isFalse();

    // when — the frozen snapshot persists and retires (the frozen put lands in the delegate)
    cache.persistFrozen();
    cache.completeFrozen(true);

    // then — the mutable tombstone still hides the now delegate-backed value
    assertThat(get(delegate, "mutableDeleted")).isEqualTo(2L);
    assertThat(get(cache, "mutableDeleted")).isEqualTo(-1L);
  }

  @Test
  void shouldScanAcrossAllLayersWithMutableWinningOverFrozenOverDelegate() {
    // given — delegate entries plus a frozen and a mutable generation of buffered writes
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "a", 1L);
    put(delegate, "c", 3L);
    put(delegate, "e", 5L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "b", 2L);
    put(cache, "c", 30L);
    delete(cache, "e"); // frozen tombstone hides the delegate's entry
    cache.freeze();
    put(cache, "c", 300L); // mutable wins over frozen and delegate
    put(cache, "d", 4L);
    delete(cache, "a"); // mutable tombstone hides the delegate's entry

    // when
    final List<String> seen = new ArrayList<>();
    cache.forEach((k, v) -> seen.add(k.toString() + "=" + v.getValue()));

    // then — merged in key order, first overlay hit wins, tombstones hide
    assertThat(seen).containsExactly("b=2", "c=300", "d=4");
  }

  @Test
  void shouldPrefixScanAcrossBothOverlaysAndDelegate() {
    // given — matching entries in the delegate, the frozen overlay, and the mutable overlay
    final KeyValueStore<DbCompositeKey<DbLong, DbString>, DbLong> delegate =
        new InMemoryKeyValueStore<>(
            new DbCompositeKey<>(new DbLong(), new DbString()), new DbLong());
    final CachingKeyValueStore<DbCompositeKey<DbLong, DbString>, DbLong> cache =
        new CachingKeyValueStore<>(
            delegate,
            () -> new DbCompositeKey<>(new DbLong(), new DbString()),
            DbLong::new,
            LARGE_BUDGET);
    delegate.put(compositeKey(1L, "a"), dbLong(1L));
    delegate.put(compositeKey(1L, "d"), dbLong(4L));
    cache.put(compositeKey(1L, "b"), dbLong(2L));
    cache.put(compositeKey(1L, "d"), dbLong(40L)); // frozen overrides the delegate
    cache.freeze();
    cache.put(compositeKey(1L, "b"), dbLong(20L)); // mutable wins over frozen
    cache.put(compositeKey(1L, "c"), dbLong(3L));
    cache.put(compositeKey(2L, "z"), dbLong(9L)); // outside the prefix

    // when
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);
    final List<String> entries = new ArrayList<>();
    cache.prefixScan(prefix, (k, v) -> entries.add(k.second().toString() + "=" + v.getValue()));
    final List<String> keys = new ArrayList<>();
    cache.prefixScanKeys(prefix, k -> keys.add(k.second().toString()));

    // then — all three layers merged under the prefix, first overlay hit wins
    assertThat(entries).containsExactly("a=1", "b=20", "c=3", "d=40");
    assertThat(keys).containsExactly("a", "b", "c", "d");
  }

  @Test
  void shouldNotIncludeWritesMadeAfterFreezeInThePersistedSnapshot() {
    // given — one frozen write, then newer writes after the freeze
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "a", 1L);
    cache.freeze();
    put(cache, "a", 2L);
    put(cache, "b", 3L);

    // when — only the frozen snapshot persists
    cache.persistFrozen();
    cache.completeFrozen(true);

    // then — the delegate holds the frozen generation; the newer writes stay buffered
    assertThat(get(delegate, "a")).isEqualTo(1L);
    assertThat(get(delegate, "b")).isEqualTo(-1L);
    assertThat(get(cache, "a")).isEqualTo(2L);

    // when — the next checkpoint flushes the newer generation
    cache.checkpoint();

    // then
    assertThat(get(delegate, "a")).isEqualTo(2L);
    assertThat(get(delegate, "b")).isEqualTo(3L);
  }

  @Test
  void shouldRetireFrozenEntriesToCleanAndEvictThemAfterCompletion() {
    // given — a tiny budget and a frozen working set far past it
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);
    for (int i = 0; i < 50; i++) {
      put(cache, "k" + i, i);
    }
    cache.freeze();
    cache.persistFrozen();

    // then — frozen entries stay pinned until completion
    assertThat(cache.overCapacity()).isTrue();

    // when — the snapshot retires
    cache.completeFrozen(true);

    // then — retired entries became clean and evictable, trimming back under budget; a trimmed
    // entry re-reads its persisted value from the delegate
    assertThat(cache.overCapacity()).isFalse();
    assertThat(delegate.gets).isZero();
    assertThat(get(cache, "k0")).isEqualTo(0L);
    assertThat(delegate.gets).isGreaterThan(0);
  }

  @Test
  void shouldMergeFrozenEntriesBackOnFailedCompletion() {
    // given — a frozen snapshot whose persist attempt failed
    final CountingDelegate delegate = new CountingDelegate();
    put(delegate, "gone", 9L);
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "a", 1L);
    delete(cache, "gone");
    cache.freeze();

    // when — the runtime aborts the checkpoint
    cache.completeFrozen(false);

    // then — the entries are dirty again and still visible
    assertThat(get(cache, "a")).isEqualTo(1L);
    assertThat(get(cache, "gone")).isEqualTo(-1L);
    assertThat(get(delegate, "a")).isEqualTo(-1L);

    // when — the next checkpoint retries them
    cache.checkpoint();

    // then
    assertThat(get(delegate, "a")).isEqualTo(1L);
    assertThat(get(delegate, "gone")).isEqualTo(-1L);
  }

  @Test
  void shouldPreferNewerMutableWritesWhenMergingBack() {
    // given — frozen writes shadowed by newer mutable ones
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "a", 1L);
    put(cache, "b", 2L);
    cache.freeze();
    put(cache, "a", 10L); // newer put wins over the frozen a=1
    delete(cache, "b"); // newer tombstone wins over the frozen b=2

    // when
    cache.completeFrozen(false);

    // then — the newer mutable writes survive the merge-back
    assertThat(get(cache, "a")).isEqualTo(10L);
    assertThat(get(cache, "b")).isEqualTo(-1L);

    // when
    cache.checkpoint();

    // then
    assertThat(get(delegate, "a")).isEqualTo(10L);
    assertThat(get(delegate, "b")).isEqualTo(-1L);
  }

  @Test
  void shouldTombstoneInsteadOfAbsorbingWhenThePutIsFrozen() {
    // given — an absorbing cache with a put already frozen (headed to the delegate)
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache =
        new CachingKeyValueStore<>(delegate, DbString::new, DbLong::new, LARGE_BUDGET, true);
    put(cache, "a", 1L);
    cache.freeze();

    // when — the delete arrives after the freeze; the frozen put persists and retires
    delete(cache, "a");
    cache.persistFrozen();
    cache.completeFrozen(true);
    assertThat(get(delegate, "a")).isEqualTo(1L);
    cache.checkpoint();

    // then — the delete could not annihilate across the frozen boundary: a real tombstone reached
    // the delegate for the persisted put
    assertThat(delegate.deletes).isEqualTo(1);
    assertThat(get(delegate, "a")).isEqualTo(-1L);
    assertThat(get(cache, "a")).isEqualTo(-1L);
  }

  @Test
  void shouldStillAbsorbAMutablePutThenDeleteWhileASnapshotIsFrozen() {
    // given — an absorbing cache with an unrelated frozen snapshot outstanding
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache =
        new CachingKeyValueStore<>(delegate, DbString::new, DbLong::new, LARGE_BUDGET, true);
    put(cache, "frozen", 1L);
    cache.freeze();

    // when — a put+delete pair lives entirely in the mutable overlay
    put(cache, "ephemeral", 7L);
    delete(cache, "ephemeral");
    cache.persistFrozen();
    cache.completeFrozen(true);
    cache.checkpoint();

    // then — the pair annihilated; only the frozen key ever reached the delegate
    assertThat(delegate.deletes).isZero();
    assertThat(get(delegate, "ephemeral")).isEqualTo(-1L);
    assertThat(get(delegate, "frozen")).isEqualTo(1L);
  }

  @Test
  void shouldCountBothOverlaysAgainstTheByteBudget() {
    // given — a tiny budget and a frozen working set past it
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, 64);
    for (int i = 0; i < 20; i++) {
      put(cache, "frozen" + i, i);
    }
    cache.freeze();

    // then — frozen entries alone keep the cache over capacity (pinned until completion)
    assertThat(cache.overCapacity()).isTrue();

    // when — the mutable overlay fills up as well
    for (int i = 0; i < 20; i++) {
      put(cache, "mutable" + i, i);
    }

    // then — still over capacity, and nothing leaked to the delegate ahead of the commit
    assertThat(cache.overCapacity()).isTrue();
    assertThat(get(delegate, "frozen0")).isEqualTo(-1L);

    // when — the frozen snapshot persists and retires, and the rest checkpoints
    cache.persistFrozen();
    cache.completeFrozen(true);
    cache.checkpoint();

    // then — everything clean, trimmed back under budget
    assertThat(cache.overCapacity()).isFalse();
  }

  @Test
  void shouldRejectAFreezeWhileASnapshotIsOutstanding() {
    // given
    final CachingKeyValueStore<DbString, DbLong> cache =
        cacheOver(new CountingDelegate(), LARGE_BUDGET);
    put(cache, "a", 1L);
    cache.freeze();

    // when / then
    assertThatThrownBy(cache::freeze)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already outstanding");
  }

  @Test
  void shouldRejectPersistAndCompleteWithoutAFrozenSnapshot() {
    // given
    final CachingKeyValueStore<DbString, DbLong> cache =
        cacheOver(new CountingDelegate(), LARGE_BUDGET);

    // when / then
    assertThatThrownBy(cache::persistFrozen).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> cache.completeFrozen(true)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldKeepEntriesDirtyWhenCheckpointFailsAndFlushThemOnRetry() {
    // given — a delegate that fails the first checkpoint attempt
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);
    put(cache, "a", 1L);
    put(cache, "b", 2L);
    delegate.failPuts = true;

    // when
    assertThatThrownBy(cache::checkpoint).isInstanceOf(IllegalStateException.class);

    // then — the writes are still buffered and served
    assertThat(get(cache, "a")).isEqualTo(1L);
    assertThat(get(cache, "b")).isEqualTo(2L);

    // when — the retried checkpoint succeeds
    delegate.failPuts = false;
    cache.checkpoint();

    // then
    assertThat(get(delegate, "a")).isEqualTo(1L);
    assertThat(get(delegate, "b")).isEqualTo(2L);
  }

  @Test
  void shouldCheckpointAnEmptyWorkingSet() {
    // given
    final CountingDelegate delegate = new CountingDelegate();
    final CachingKeyValueStore<DbString, DbLong> cache = cacheOver(delegate, LARGE_BUDGET);

    // when — freeze/persist/complete with nothing buffered, then the composed checkpoint
    cache.freeze();
    cache.persistFrozen();
    cache.completeFrozen(true);
    cache.checkpoint();

    // then — no writes reached the delegate
    assertThat(delegate.deletes).isZero();
    assertThat(get(delegate, "anything")).isEqualTo(-1L);
  }

  private static DbCompositeKey<DbLong, DbString> compositeKey(
      final long first, final String second) {
    final DbCompositeKey<DbLong, DbString> key = new DbCompositeKey<>(new DbLong(), new DbString());
    key.first().wrapLong(first);
    key.second().wrapString(second);
    return key;
  }

  private static DbLong dbLong(final long value) {
    final DbLong dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  private static boolean exists(final KeyValueStore<DbString, DbLong> store, final String key) {
    final DbString k = new DbString();
    k.wrapString(key);
    return store.exists(k);
  }

  private static CachingKeyValueStore<DbString, DbLong> cacheOver(
      final KeyValueStore<DbString, DbLong> delegate, final long maxBytes) {
    return new CachingKeyValueStore<>(delegate, DbString::new, DbLong::new, maxBytes);
  }

  private static long get(final KeyValueStore<DbString, DbLong> store, final String key) {
    final DbString k = new DbString();
    k.wrapString(key);
    return store.get(k).map(DbLong::getValue).orElse(-1L);
  }

  private static void put(
      final KeyValueStore<DbString, DbLong> store, final String key, final long value) {
    final DbString k = new DbString();
    k.wrapString(key);
    final DbLong v = new DbLong();
    v.wrapLong(value);
    store.put(k, v);
  }

  private static void delete(final KeyValueStore<DbString, DbLong> store, final String key) {
    final DbString k = new DbString();
    k.wrapString(key);
    store.delete(k);
  }

  /** An in-memory delegate that counts read-throughs, to observe caching and eviction. */
  private static final class CountingDelegate implements KeyValueStore<DbString, DbLong> {

    private final KeyValueStore<DbString, DbLong> inner =
        new InMemoryKeyValueStore<>(new DbString(), new DbLong());
    private int gets;
    private int deletes;
    private boolean failPuts;

    @Override
    public Optional<DbLong> get(final DbString key) {
      gets++;
      return inner.get(key);
    }

    @Override
    public boolean exists(final DbString key) {
      return inner.exists(key);
    }

    @Override
    public void prefixScan(final DbKey prefix, final BiConsumer<DbString, DbLong> visitor) {
      inner.prefixScan(prefix, visitor);
    }

    @Override
    public void prefixScanKeys(final DbKey prefix, final Consumer<DbString> visitor) {
      inner.prefixScanKeys(prefix, visitor);
    }

    @Override
    public void forEach(final BiConsumer<DbString, DbLong> visitor) {
      inner.forEach(visitor);
    }

    @Override
    public void put(final DbString key, final DbLong value) {
      if (failPuts) {
        throw new IllegalStateException("delegate write failure (injected)");
      }
      inner.put(key, value);
    }

    @Override
    public void delete(final DbString key) {
      deletes++;
      inner.delete(key);
    }
  }
}
