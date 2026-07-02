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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import org.junit.jupiter.api.Test;

final class WriteBackKeyValueStoreTest {

  private static KeyValueStore<DbString, DbLong> newDelegate() {
    return new InMemoryKeyValueStore<>(new DbString(), new DbLong());
  }

  private static WriteBackKeyValueStore<DbString, DbLong> cacheOver(
      final KeyValueStore<DbString, DbLong> delegate) {
    return new WriteBackKeyValueStore<>(delegate, new DbString(), new DbLong());
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

  @Test
  void shouldReadOwnWritesWithoutTouchingTheDelegate() {
    // given
    final KeyValueStore<DbString, DbLong> delegate = newDelegate();
    final WriteBackKeyValueStore<DbString, DbLong> cache = cacheOver(delegate);

    // when — a write lands in the cache but is not checkpointed
    put(cache, "a", 7L);

    // then — the cache serves it, the delegate has nothing yet
    assertThat(get(cache, "a")).isEqualTo(7L);
    assertThat(get(delegate, "a")).isEqualTo(-1L);
  }

  @Test
  void shouldFlushChangesAndDeletesOnCheckpoint() {
    // given — a delegate pre-loaded with a key the cache will later delete
    final KeyValueStore<DbString, DbLong> delegate = newDelegate();
    put(delegate, "old", 1L);
    final WriteBackKeyValueStore<DbString, DbLong> cache = cacheOver(delegate);

    // when — one upsert and one delete, then a checkpoint
    put(cache, "new", 42L);
    final DbString oldKey = new DbString();
    oldKey.wrapString("old");
    cache.delete(oldKey);
    cache.checkpoint();

    // then — the delegate reflects both the write and the delete
    assertThat(get(delegate, "new")).isEqualTo(42L);
    assertThat(get(delegate, "old")).isEqualTo(-1L);
  }

  @Test
  void shouldRecoverStateFromTheDelegateOnConstruction() {
    // given — a delegate already holding state (a prior run's checkpoint)
    final KeyValueStore<DbString, DbLong> delegate = newDelegate();
    put(delegate, "a", 1L);
    put(delegate, "b", 2L);

    // when — a fresh cache is opened over it
    final WriteBackKeyValueStore<DbString, DbLong> cache = cacheOver(delegate);

    // then — it serves the recovered state from the heap
    assertThat(get(cache, "a")).isEqualTo(1L);
    assertThat(get(cache, "b")).isEqualTo(2L);
  }
}
