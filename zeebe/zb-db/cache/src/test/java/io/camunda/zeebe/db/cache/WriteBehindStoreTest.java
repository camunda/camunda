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

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class WriteBehindStoreTest {

  private static final Duration STALLED = Duration.ofHours(1);
  private static final Duration QUICK = Duration.ofMillis(10);
  private static final long UNLIMITED = Long.MAX_VALUE;

  private final InMemoryStorage storage = new InMemoryStorage();
  private final StorageColumnFamilies<TestColumnFamily> reader =
      new StorageColumnFamilies<>(storage.db(), storage.db().createContext());

  @Test
  void shouldReadCommittedValueBeforeTheStorageHasIt() {
    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // then
      assertThat(store.get(key(1), reader)).containsExactly(10);
      assertThat(storage.get(FIRST, bytes(1))).isNull();
    }
  }

  @Test
  void shouldReadCommittedDeleteBeforeTheStorageHasIt() {
    // given
    storage.put(FIRST, bytes(1), bytes(10));

    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(null)));

      // then
      assertThat(store.get(key(1), reader)).isNull();
      assertThat(storage.get(FIRST, bytes(1))).containsExactly(10);
    }
  }

  @Test
  void shouldReadValueThatOnlyTheStorageHas() {
    // given
    storage.put(FIRST, bytes(1), bytes(10));

    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      // when
      final var value = store.get(key(1), reader);

      // then
      assertThat(value).containsExactly(10);
    }
  }

  @Test
  void shouldWriteCommittedPutsAndDeletesToTheStorage() {
    // given
    storage.put(FIRST, bytes(2), bytes(20));

    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10)), key(2), new Op(null)));

      // then
      await()
          .untilAsserted(
              () -> {
                assertThat(storage.get(FIRST, bytes(1))).containsExactly(10);
                assertThat(storage.get(FIRST, bytes(2))).isNull();
              });
    }
  }

  @Test
  void shouldReadPendingWritesAfterTheyAreEvicted() {
    // given
    final var keys = IntStream.range(0, 100).boxed().toList();
    keys.forEach(i -> storage.put(FIRST, bytes(i), new byte[100]));
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
      await().untilAsserted(() -> assertThat(storage.get(FIRST, bytes(1))).containsExactly(10, 11));
    }
  }

  @Test
  void shouldAgreeWithTheStorageAfterConcurrentCommitsOfTheSameKey() throws Exception {
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
          .untilAsserted(() -> assertThat(storage.get(FIRST, bytes(1))).isEqualTo(lastCommitted));
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
      assertThat(storage.get(FIRST, bytes(1))).isEqualTo(largeValue);
    }
  }

  @Test
  void shouldCatchUpWithEveryCommitMadeBeforeIt() {
    // given
    storage.put(FIRST, bytes(2), bytes(20));

    try (final var store = store(STALLED, UNLIMITED, UNLIMITED)) {
      store.commit(Map.of(key(1), new Op(bytes(10))));
      store.commit(Map.of(key(2), new Op(null)));

      // when
      store.catchUp();

      // then
      assertThat(storage.get(FIRST, bytes(1))).containsExactly(10);
      assertThat(storage.get(FIRST, bytes(2))).isNull();
    }
  }

  @Test
  void shouldGiveUpCatchingUpWithAStalledWriterWithoutHoldingCommitsBack() throws Exception {
    // given
    final var store =
        new WriteBehindStore<>(
            storage.db(),
            new TestConfiguration()
                .maxWriteDelay(QUICK)
                .snapshotCatchUpTimeout(Duration.ofMillis(200))
                .build());
    try {
      storage.pauseTransactions();
      store.commit(Map.of(key(1), new Op(bytes(10))));
      storage.awaitPausedTransaction();

      // when
      final var catchingUp = CompletableFuture.runAsync(store::catchUp);
      store.commit(Map.of(key(2), new Op(bytes(20))));

      // then
      assertThat(catchingUp)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .havingRootCause()
          .isInstanceOf(TimeoutException.class);
    } finally {
      storage.resumeTransactions();
      store.close();
    }
  }

  @Test
  void shouldWriteATransactionOnceTheLogCommittedTheRecordsWrittenBeforeIt() {
    // given - the stream processor wrote up to position 5 before the commit and 6 after it
    final var gate = new TestCommitGate();
    try (final var store =
        new WriteBehindStore<>(
            storage.db(),
            new TestConfiguration()
                .maxWriteDelay(QUICK)
                .snapshotCatchUpTimeout(Duration.ofMillis(200))
                .build(),
            gate)) {
      gate.write(5);
      store.commit(Map.of(key(1), new Op(bytes(10))));
      gate.write(6);
      assertThatThrownBy(store::catchUp).hasRootCauseInstanceOf(TimeoutException.class);
      assertThat(storage.get(FIRST, bytes(1))).isNull();

      // when
      gate.commit(5);

      // then
      store.catchUp();
      assertThat(storage.get(FIRST, bytes(1))).containsExactly(10);
    }
  }

  @Test
  void shouldFailCommitsOnceAnEnqueueFails() {
    // given
    final var store = store(STALLED, UNLIMITED, UNLIMITED);
    store.close();

    // when
    assertThatThrownBy(() -> store.commit(Map.of(key(1), new Op(bytes(10)))))
        .isInstanceOf(IllegalStateException.class);

    // then
    assertThatThrownBy(() -> store.commit(Map.of(key(2), new Op(bytes(20)))))
        .isInstanceOf(UnrecoverableException.class);
    store.commit(Map.of());
    assertThat(store.failure()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRetryARecoverableStorageError() {
    // given
    storage.failTransactions(2, () -> new ZeebeDbException("expected", new RuntimeException()));

    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));
      store.catchUp();

      // then
      assertThat(storage.get(FIRST, bytes(1))).containsExactly(10);
      assertThat(store.failure()).isNull();
    }
  }

  @Test
  void shouldFailOnceRetriesRunOut() {
    // given
    storage.failTransactions(
        Integer.MAX_VALUE, () -> new ZeebeDbException("expected", new RuntimeException()));

    try (final var store = store(QUICK, UNLIMITED, UNLIMITED)) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // then
      await().untilAsserted(() -> assertThat(store.failure()).isInstanceOf(ZeebeDbException.class));
      assertThatThrownBy(() -> store.commit(Map.of(key(2), new Op(bytes(20)))))
          .isInstanceOf(UnrecoverableException.class);
    }
  }

  @Test
  void shouldAbortWaitersButKeepReadingOnceTheStorageFailsForGood() throws Exception {
    // given - the writer is paused on the first commit; the second commit and a snapshot's catch-up
    // wait for it, and neither would give up within the test
    final var store =
        new WriteBehindStore<>(
            storage.db(),
            new TestConfiguration()
                .maxWriteDelay(QUICK)
                .maxPendingBytes(0)
                .snapshotCatchUpTimeout(Duration.ofMinutes(5))
                .build());
    try {
      storage.pauseTransactions();
      store.commit(Map.of(key(1), new Op(bytes(10))));
      storage.awaitPausedTransaction();
      final var catchingUp = CompletableFuture.runAsync(store::catchUp);
      final var committing =
          CompletableFuture.runAsync(() -> store.commit(Map.of(key(2), new Op(bytes(20)))));

      // when
      storage.failTransactions(1, () -> new IllegalStateException("expected"));
      storage.resumeTransactions();

      // then
      assertThat(catchingUp)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(UnrecoverableException.class);
      assertThat(committing)
          .failsWithin(Duration.ofSeconds(5))
          .withThrowableThat()
          .havingCause()
          .isInstanceOf(UnrecoverableException.class);
      assertThat(store.get(key(1), reader)).containsExactly(10);
      store.commit(Map.of());
      assertThatThrownBy(() -> store.commit(Map.of(key(3), new Op(bytes(30)))))
          .isInstanceOf(UnrecoverableException.class);
      assertThatThrownBy(store::catchUp).isInstanceOf(UnrecoverableException.class);
    } finally {
      storage.resumeTransactions();
      store.close();
    }
  }

  private WriteBehindStore<TestColumnFamily> store(
      final Duration maxWriteDelay, final long cacheMaxBytes, final long maxPendingBytes) {
    return new WriteBehindStore<>(
        storage.db(),
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
