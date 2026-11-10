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
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class WriteBehindStoreTest {

  private static final Duration STALLED = Duration.ofHours(1);
  private static final Duration QUICK = Duration.ofMillis(10);
  private static final long UNLIMITED = Long.MAX_VALUE;

  private final InMemoryBackend backend = new InMemoryBackend();
  private final PassThroughColumnFamilies<TestColumnFamily> reader =
      new PassThroughColumnFamilies<>(backend.db(), backend.db().createContext());

  @Test
  void shouldReadCommittedValueBeforeTheBackendHasIt() {
    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // then
      assertThat(store.get(key(1), reader)).containsExactly(10);
      assertThat(backend.get(FIRST, bytes(1))).isNull();
    }
  }

  @Test
  void shouldReadCommittedDeleteBeforeTheBackendHasIt() {
    // given
    backend.put(FIRST, bytes(1), bytes(10));

    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(null)));

      // then
      assertThat(store.get(key(1), reader)).isNull();
      assertThat(backend.get(FIRST, bytes(1))).containsExactly(10);
    }
  }

  @Test
  void shouldReadValueThatOnlyTheBackendHas() {
    // given
    backend.put(FIRST, bytes(1), bytes(10));

    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      final var value = store.get(key(1), reader);

      // then
      assertThat(value).containsExactly(10);
    }
  }

  @Test
  void shouldWriteCommittedPutsAndDeletesToTheBackend() {
    // given
    backend.put(FIRST, bytes(2), bytes(20));

    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10)), key(2), new Op(null)));

      // then
      await()
          .untilAsserted(
              () -> {
                assertThat(backend.get(FIRST, bytes(1))).containsExactly(10);
                assertThat(backend.get(FIRST, bytes(2))).isNull();
              });
    }
  }

  @Test
  void shouldReadPendingWritesAfterTheyAreEvicted() {
    // given
    final var keys = IntStream.range(0, 100).boxed().toList();
    keys.forEach(i -> backend.put(FIRST, bytes(i), new byte[100]));
    final var newValue = new byte[100];
    Arrays.fill(newValue, (byte) 1);

    try (final var store = store(STALLED, 1024, UNLIMITED)) {
      // when
      keys.forEach(i -> store.commit(Map.of(key(i), new Op(newValue))));

      // then
      assertThat(keys).allSatisfy(i -> assertThat(store.get(key(i), reader)).isEqualTo(newValue));
    }
  }

  @Test
  void shouldNotLetCallersChangeCommittedState() {
    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // given
      store.commit(Map.of(key(1), new Op(bytes(10, 11))));

      // when
      final var value = store.get(key(1), reader);
      value[0] = 99;

      // then
      assertThat(store.get(key(1), reader)).containsExactly(10, 11);
      await().untilAsserted(() -> assertThat(backend.get(FIRST, bytes(1))).containsExactly(10, 11));
    }
  }

  @Test
  void shouldAgreeWithTheBackendAfterConcurrentCommitsOfTheSameKey() throws Exception {
    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // when
      final var committers =
          IntStream.range(0, 2)
              .mapToObj(
                  committer ->
                      CompletableFuture.runAsync(
                          () ->
                              IntStream.range(0, 500)
                                  .forEach(
                                      i ->
                                          store.commit(
                                              Map.of(key(1), new Op(bytes(committer, i)))))))
              .toList();
      CompletableFuture.allOf(committers.toArray(CompletableFuture[]::new))
          .get(30, TimeUnit.SECONDS);

      // then
      final var lastCommitted = store.get(key(1), reader);
      await()
          .untilAsserted(() -> assertThat(backend.get(FIRST, bytes(1))).isEqualTo(lastCommitted));
    }
  }

  @Test
  void shouldWaitForTheWriterWhilePendingBytesExceedTheLimit() {
    // given
    final var largeValue = new byte[1024 * 1024];

    try (final var store = store(QUICK, UNLIMITED, 1024 * 1024)) {
      store.commit(Map.of(key(1), new Op(largeValue)));

      // when
      store.commit(Map.of(key(2), new Op(largeValue)));

      // then
      assertThat(backend.get(FIRST, bytes(1))).isEqualTo(largeValue);
    }
  }

  @Test
  void shouldFailCommitsAndReadsOnceAnEnqueueFails() {
    // given
    final var store = store(STALLED, UNLIMITED, UNLIMITED);
    store.close();

    // when
    assertThatThrownBy(() -> store.commit(Map.of(key(1), new Op(bytes(10)))))
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThatThrownBy(() -> store.commit(Map.of(key(2), new Op(bytes(20)))))
        .isInstanceOf(UnrecoverableException.class);
    assertThatThrownBy(() -> store.commit(Map.of())).isInstanceOf(UnrecoverableException.class);
    assertThatThrownBy(() -> store.get(key(1), reader)).isInstanceOf(UnrecoverableException.class);
  }

  private WriteBehindStore<TestColumnFamily> store(
      final Duration maxWriteDelay, final long cacheMaxBytes, final long maxPendingBytes) {
    return new WriteBehindStore<>(
        backend.db(),
        new TestConfiguration()
            .maxWriteDelay(maxWriteDelay)
            .cacheMaxBytes(cacheMaxBytes)
            .maxPendingBytes(maxPendingBytes)
            .build());
  }

  private static CacheKey key(final int key) {
    return new CacheKey(FIRST, bytes(key));
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}
