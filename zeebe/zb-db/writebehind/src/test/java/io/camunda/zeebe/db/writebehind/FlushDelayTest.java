/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class FlushDelayTest {

  private static final Duration LONG_DELAY = Duration.ofHours(1);

  private final InMemoryRawDbAccess storage = new InMemoryRawDbAccess();
  private WriteBehindStore<RawKey, DbValue> store;

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
    awaitInStorage(1);
  }

  @Test
  void shouldNeverWriteKeyThatIsDeletedWithinTheDelay() {
    // given
    open(config(LONG_DELAY));
    put(1);
    put(2);

    // when
    delete(1);
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
    assertThat(storage.get(key(1).bytes(), key(1).bytes().length)).isEqualTo(encoded(5));
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
    awaitInStorage(1);
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

  /** Polls until the writer wrote the key; the test fails if it doesn't within a generous time. */
  private void awaitInStorage(final long id) {
    final long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!inStorage(id) && System.nanoTime() < deadline) {
      LockSupport.parkNanos(Duration.ofMillis(5).toNanos());
    }
    assertThat(inStorage(id)).isTrue();
  }

  private boolean inStorage(final long id) {
    final var bytes = key(id).bytes();
    return storage.get(bytes, bytes.length) != null;
  }

  private void put(final long id) {
    put(id, id);
  }

  private void put(final long id, final long value) {
    store.commit(new RawKey[] {key(id)}, new DbValue[] {valueOf(value)});
  }

  private void delete(final long id) {
    store.commit(new RawKey[] {key(id)}, new DbValue[1]);
  }

  private static RawKey key(final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(7).putLong(id).array());
  }

  private static DbLong valueOf(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }

  private static byte[] encoded(final long value) {
    return Values.encode(valueOf(value), new org.agrona.ExpandableArrayBuffer());
  }
}
