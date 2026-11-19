/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.DbValue;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Forces one interleaving of a committing, reading or snapshotting thread with another, by pausing
 * a thread inside the store, so that each race shows on every run.
 */
final class StoreInterleavingTest {

  // nothing is flushed until a test asks for it
  private static final WriteBehindConfiguration CONFIG =
      WriteBehindConfiguration.defaults().withFlushDelay(Duration.ofHours(1));

  private final InMemoryRawDbAccess storage = new InMemoryRawDbAccess();
  private final PausingBackend backend = new PausingBackend(storage);
  private final WriteBehindStore<RawKey, HeldValue, DbValue> store =
      new WriteBehindStore<>(backend, CONFIG);

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldWaitForCommitThatIsBeingPublishedWhenAwaitingTheFlush() throws Exception {
    // given a commit that has published its first key, but not its second one
    final var committer = new TestThread(() -> commit(key(1), held(1), key(2), held(2)));
    final var pause = backend.pauseOnWeigh(committer.thread, key(2));
    committer.start();
    pause.awaitReached();

    // when
    final var flushedKey = new AtomicReference<@Nullable Boolean>();
    final var snapshot =
        new TestThread(
            () -> {
              store.awaitFlushed();
              flushedKey.set(inStorage(key(1)));
            });
    snapshot.start();
    await().until(snapshot::isWaitingOrDone);
    pause.release();
    committer.join();
    snapshot.join();

    // then
    assertThat(flushedKey.get()).isTrue();
  }

  @Test
  void shouldNotReadPartOfCommitThatIsBeingPublished() throws Exception {
    // given an unflushed commit, and a later one that has published its first key only
    commit(key(1), held(1), key(2), held(1));
    final var committer = new TestThread(() -> commit(key(1), held(2), key(2), held(2)));
    final var pause = backend.pauseOnWeigh(committer.thread, key(2));
    committer.start();
    pause.awaitReached();

    // when
    final var first = longOf(key(1));
    final var second = longOf(key(2));
    pause.release();
    committer.join();

    // then
    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(1);
    assertThat(longOf(key(1))).isEqualTo(2);
  }

  @Test
  void shouldReadFlushedValueOfKeyThatACommitBeingPublishedChanges() throws Exception {
    // given a flushed value, and a commit that has published a new value for it only
    commit(key(1), held(1), key(2), held(1));
    store.awaitFlushed();
    final var committer = new TestThread(() -> commit(key(1), held(2), key(2), held(2)));
    final var pause = backend.pauseOnWeigh(committer.thread, key(2));
    committer.start();
    pause.awaitReached();

    // when
    final var first = longOf(key(1));
    pause.release();
    committer.join();

    // then
    assertThat(first).isEqualTo(1);
  }

  @Test
  void shouldNotFindKeyThatACommitBeingPublishedCreates() throws Exception {
    // given a commit that has published the first of two new keys only
    final var committer = new TestThread(() -> commit(key(1), held(1), key(2), held(1)));
    final var pause = backend.pauseOnWeigh(committer.thread, key(2));
    committer.start();
    pause.awaitReached();

    // when
    final var value = store.get(key(1));
    final var exists = store.exists(key(1));
    pause.release();
    committer.join();

    // then
    assertThat(value).isNull();
    assertThat(exists).isFalse();
    assertThat(store.exists(key(1))).isTrue();
  }

  @Test
  void shouldNotCacheLoadedValueOverNewerFlushedValue() throws Exception {
    // given a read that loaded a stored value and is about to cache it
    storeDirectly(key(1), held(1));
    final var reader = new TestThread(() -> longOf(key(1)));
    final var pause = backend.pauseOnWeigh(reader.thread, key(1));
    reader.start();
    pause.awaitReached();

    // when a newer value is flushed before the read caches the loaded one
    TestValues.commit(store, key(1), held(2));
    store.awaitFlushed();
    pause.release();
    reader.join();

    // then
    assertThat(longOf(key(1))).isEqualTo(2);
  }

  @Test
  void shouldNotCacheLoadedValueOfKeyThatWasDeletedSince() throws Exception {
    // given a read that loaded a stored value and is about to cache it
    storeDirectly(key(1), held(1));
    final var reader = new TestThread(() -> longOf(key(1)));
    final var pause = backend.pauseOnWeigh(reader.thread, key(1));
    reader.start();
    pause.awaitReached();

    // when the key is deleted and flushed before the read caches the loaded value
    TestValues.commit(store, key(1), null);
    store.awaitFlushed();
    pause.release();
    reader.join();

    // then
    assertThat(longOf(key(1))).isNull();
    assertThat(store.exists(key(1))).isFalse();
  }

  private void storeDirectly(final RawKey key, final HeldValue value) {
    try (final var batch = storage.newBatch()) {
      batch.put(key.bytes(), value.bytes());
      batch.write();
    }
  }

  private @Nullable Long longOf(final RawKey key) {
    return TestValues.longOf(store.get(key));
  }

  private void commit(
      final RawKey first, final HeldValue firstValue, final RawKey second, final HeldValue value) {
    store.commit(new RawKey[] {first, second}, new HeldValue[] {firstValue, value});
  }

  private boolean inStorage(final RawKey key) {
    return storage.get(key.bytes(), key.bytes().length) != null;
  }

  /** A thread whose failure the test sees when it joins it. */
  private static final class TestThread {
    private final Thread thread;
    private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();

    private TestThread(final Runnable task) {
      thread =
          new Thread(
              () -> {
                try {
                  task.run();
                } catch (final Throwable e) {
                  failure.set(e);
                }
              });
      thread.setDaemon(true);
    }

    void start() {
      thread.start();
    }

    /** Whether it parked, for example on a lock, or ended. */
    boolean isWaitingOrDone() {
      final var state = thread.getState();
      return state == Thread.State.WAITING
          || state == Thread.State.TIMED_WAITING
          || state == Thread.State.TERMINATED;
    }

    void join() throws Exception {
      thread.join(Duration.ofSeconds(30));
      assertThat(thread.isAlive()).as("thread ended in time").isFalse();
      final var cause = failure.get();
      if (cause != null) {
        throw new AssertionError("Expected the thread to succeed, but it failed", cause);
      }
    }
  }
}
