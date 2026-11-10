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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import org.junit.jupiter.api.Test;

final class BackendLoaderWriterTest {

  private final InMemoryBackend backend = new InMemoryBackend();
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final AtomicLong lastCommittedSeq = new AtomicLong();
  private final AtomicInteger flushes = new AtomicInteger();
  private final BackendLoaderWriter<TestColumnFamily> loaderWriter =
      new BackendLoaderWriter<>(
          backend.db(),
          dirtyIndex,
          new StampedLock(),
          lastCommittedSeq::get,
          flushes::incrementAndGet);
  private final PassThroughColumnFamilies<TestColumnFamily> reader =
      new PassThroughColumnFamilies<>(backend.db(), backend.db().createContext());

  @Test
  void shouldWriteBatchInOneBackendTransaction() {
    // given
    backend.put(FIRST, bytes(2), bytes(20));
    final var put = Map.entry(new CacheKey(FIRST, bytes(1)), new CachedValue(bytes(10), 1));
    final var delete = Map.entry(new CacheKey(FIRST, bytes(2)), new CachedValue(null, 1));

    // when
    loaderWriter.writeAll(List.of(put, delete));

    // then
    assertThat(backend.get(FIRST, bytes(1))).containsExactly(10);
    assertThat(backend.get(FIRST, bytes(2))).isNull();
    assertThat(backend.transactions()).isOne();
  }

  @Test
  void shouldRemoveWrittenVersionsFromDirtyIndex() {
    // given
    final var first = new CacheKey(FIRST, bytes(1));
    final var second = new CacheKey(FIRST, bytes(2));
    final var firstValue = new CachedValue(bytes(10), 1);
    final var secondValue = new CachedValue(null, 1);
    dirtyIndex.put(first, firstValue);
    dirtyIndex.put(second, secondValue);

    // when
    loaderWriter.writeAll(List.of(Map.entry(first, firstValue), Map.entry(second, secondValue)));

    // then
    assertThat(dirtyIndex.isEmpty()).isTrue();
    assertThat(dirtyIndex.bytes()).isZero();
    assertThat(flushes).hasValue(1);
  }

  @Test
  void shouldKeepVersionPublishedWhileAnOlderOneWasWritten() {
    // given
    final var key = new CacheKey(FIRST, bytes(1));
    final var older = new CachedValue(bytes(10), 1);
    final var newer = new CachedValue(bytes(20), 2);
    dirtyIndex.put(key, newer);

    // when
    loaderWriter.writeAll(List.of(Map.entry(key, older)));

    // then
    assertThat(dirtyIndex.get(key)).isSameAs(newer);
  }

  @Test
  void shouldLoadUnflushedVersionInsteadOfTheBackendValue() {
    // given
    backend.put(FIRST, bytes(1), bytes(10));
    final var key = new CacheKey(FIRST, bytes(1));
    final var unflushed = new CachedValue(bytes(20), 3);
    dirtyIndex.put(key, unflushed);

    // when
    final var loaded = loaderWriter.readThrough(reader, () -> loaderWriter.load(key));

    // then
    assertThat(loaded).isSameAs(unflushed);
  }

  @Test
  void shouldLoadBackendValueStampedWithLastCommittedSeq() {
    // given
    backend.put(FIRST, bytes(1), bytes(10));
    lastCommittedSeq.set(7);

    // when
    final var loaded =
        loaderWriter.readThrough(reader, () -> loaderWriter.load(new CacheKey(FIRST, bytes(1))));

    // then
    assertThat(loaded.bytes()).containsExactly(10);
    assertThat(loaded.seq()).isEqualTo(7);
  }

  @Test
  void shouldLoadKeyMissingFromBackendAsTombstone() {
    // given
    lastCommittedSeq.set(7);

    // when
    final var loaded =
        loaderWriter.readThrough(reader, () -> loaderWriter.load(new CacheKey(FIRST, bytes(1))));

    // then
    assertThat(loaded.isTombstone()).isTrue();
    assertThat(loaded.seq()).isEqualTo(7);
  }

  @Test
  void shouldRejectLoadOutsideReadThrough() {
    // given
    final var key = new CacheKey(FIRST, bytes(1));

    // when - then
    assertThatThrownBy(() -> loaderWriter.load(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("readThrough");
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}
