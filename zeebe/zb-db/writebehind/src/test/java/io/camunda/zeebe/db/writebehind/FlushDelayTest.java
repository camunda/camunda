/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.commit;
import static io.camunda.zeebe.db.writebehind.TestValues.held;
import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class FlushDelayTest {

  private static final Duration LONG_DELAY = Duration.ofHours(1);

  private final InMemoryRawDbAccess storage = new InMemoryRawDbAccess();
  private WriteBehindStore<RawKey, HeldValue, DbValue> store;

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
  }

  private void open(final WriteBehindConfiguration config) {
    store = new WriteBehindStore<>(new RawBackend(storage), config);
  }

  private static WriteBehindConfiguration config(final Duration delay) {
    return WriteBehindConfiguration.defaults().withFlushDelay(delay);
  }

  @Test
  void shouldKeepCommitOutOfStorageWhileItWaitsForTheDelay() {
    // given
    open(config(LONG_DELAY));

    // when
    put(1);

    // then
    assertThat(inStorage(1)).isFalse();
    assertThat(store.get(key(1), new DbLong())).isNotNull();
  }

  @Test
  void shouldWriteCommitAfterTheDelay() {
    // given
    open(config(Duration.ofMillis(200)));

    // when
    put(1);

    // then
    await().until(() -> inStorage(1));
  }

  @Test
  void shouldNeverWriteKeyThatIsDeletedWithinTheDelay() {
    // given
    open(config(LONG_DELAY));
    put(1);
    put(2);

    // when
    commit(store, key(1), null);
    store.awaitFlushed();

    // then
    assertThat(inStorage(1)).isFalse();
    assertThat(inStorage(2)).isTrue();
  }

  @Test
  void shouldWriteOnlyLatestValueOfKeyThatChangedWithinTheDelay() {
    // given
    open(config(LONG_DELAY));
    put(1);
    put(1, 5);

    // when
    store.awaitFlushed();

    // then
    assertThat(storage.get(key(1).bytes(), key(1).bytes().length)).isEqualTo(held(5).bytes());
  }

  @Test
  void shouldFlushAtOnceWhenSnapshotWaits() {
    // given
    open(config(LONG_DELAY));
    put(1);

    // when
    store.awaitFlushed();

    // then
    assertThat(inStorage(1)).isTrue();
  }

  @Test
  void shouldFlushEarlyWhenUnflushedBytesReachTheThreshold() {
    // given
    open(config(LONG_DELAY).withFlushThresholdBytes(100));

    // when
    put(1);

    // then
    await().until(() -> inStorage(1));
  }

  @Test
  void shouldFlushWhenStoreCloses() {
    // given
    open(config(LONG_DELAY));
    put(1);

    // when
    store.close();

    // then
    assertThat(inStorage(1)).isTrue();
  }

  private boolean inStorage(final long id) {
    final var bytes = key(id).bytes();
    return storage.get(bytes, bytes.length) != null;
  }

  private void put(final long id) {
    put(id, id);
  }

  private void put(final long id, final long value) {
    commit(store, key(id), held(value));
  }
}
