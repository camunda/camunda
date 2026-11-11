/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class StorageLoaderTest {

  private final InMemoryStorage storage = new InMemoryStorage();
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final StorageLoader<TestColumnFamily> loader =
      new StorageLoader<>(dirtyIndex, new WriteBehindMetrics(storage.meterRegistry()));
  private final StorageColumnFamilies<TestColumnFamily> reader =
      new StorageColumnFamilies<>(storage.db(), storage.db().createContext());

  @Test
  void shouldLoadUnflushedVersionInsteadOfTheStorageValue() {
    // given
    storage.put(FIRST, bytes(1), bytes(10));
    final var key = new CacheKey(FIRST, bytes(1));
    final var unflushed = new CachedValue(bytes(20), 3);
    dirtyIndex.put(key, unflushed);

    // when
    final var loaded = loader.readThrough(reader, () -> loader.load(key));

    // then
    assertThat(loaded).isSameAs(unflushed);
  }

  @Test
  void shouldLoadStorageValue() {
    // given
    storage.put(FIRST, bytes(1), bytes(10));

    // when
    final var loaded = loader.readThrough(reader, () -> loader.load(new CacheKey(FIRST, bytes(1))));

    // then
    assertThat(loaded.bytes()).containsExactly(10);
  }

  @Test
  void shouldLoadKeyMissingFromStorageAsTombstone() {
    // when
    final var loaded = loader.readThrough(reader, () -> loader.load(new CacheKey(FIRST, bytes(1))));

    // then
    assertThat(loaded.isTombstone()).isTrue();
  }

  @Test
  void shouldRejectLoadOutsideReadThrough() {
    // given
    final var key = new CacheKey(FIRST, bytes(1));

    // when - then
    assertThatThrownBy(() -> loader.load(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readThrough");
  }

  @Test
  void shouldNotWriteToTheStorage() {
    // when
    loader.write(new CacheKey(FIRST, bytes(1)), new CachedValue(bytes(10), 1));

    // then
    assertThat(storage.get(FIRST, bytes(1))).isNull();
    assertThat(storage.transactions()).isZero();
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}
