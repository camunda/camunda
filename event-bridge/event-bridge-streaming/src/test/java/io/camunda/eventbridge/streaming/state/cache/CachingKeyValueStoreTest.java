/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
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

  private static CachingKeyValueStore<DbString, DbLong> cacheOver(
      final KeyValueStore<DbString, DbLong> delegate, final long maxBytes) {
    return new CachingKeyValueStore<>(delegate, new DbString(), new DbLong(), maxBytes);
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
    public void forEach(final BiConsumer<DbString, DbLong> visitor) {
      inner.forEach(visitor);
    }

    @Override
    public void put(final DbString key, final DbLong value) {
      inner.put(key, value);
    }

    @Override
    public void delete(final DbString key) {
      inner.delete(key);
    }
  }
}
