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

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbLong;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** What a scan sees of the unflushed commits, from the moment that it opened. */
final class ScanViewTest {

  private static final KeyRange<RawKey> ALL = new KeyRange<>(key(0), key(100));

  private final InMemoryRawDbAccess storage = new InMemoryRawDbAccess();
  // a long delay, so that the commits stay in the active batch until a flush is requested
  private final WriteBehindStore<RawKey, HeldValue, DbValue> store =
      new WriteBehindStore<>(
          new RawBackend(storage),
          WriteBehindConfiguration.defaults().withFlushDelay(Duration.ofHours(1)));

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldNotSeeCommitsAfterItOpened() {
    // given
    commit(store, key(1), held(1));
    commit(store, key(3), held(3));

    // when
    try (final var scan = new ViewSource<>(store.backend(), store.openView(ALL))) {
      commit(store, key(1), held(10));
      commit(store, key(2), held(2));
      commit(store, key(3), null);

      // then
      assertThat(forward(scan)).containsExactly(1L, 3L);
      assertThat(readValue(scan, key(1))).isEqualTo(1);
    }
  }

  @Test
  void shouldKeepSeeingTheUnflushedStateWhileItIsFlushed() {
    // given
    commit(store, key(1), held(1));
    commit(store, key(2), held(2));

    // when
    try (final var scan = new ViewSource<>(store.backend(), store.openView(ALL))) {
      commit(store, key(2), held(20));
      store.awaitFlushed();
      commit(store, key(4), held(4));

      // then
      assertThat(forward(scan)).containsExactly(1L, 2L);
      assertThat(readValue(scan, key(2))).isEqualTo(2);
    }
  }

  @Test
  void shouldPreferTheActiveBatchOverTheSealedOne() {
    // given - the first commit gets flushed, so that it sits in storage or the sealed batch
    commit(store, key(1), held(1));
    commit(store, key(2), held(2));
    store.awaitFlushed();
    commit(store, key(1), null);
    commit(store, key(2), held(20));

    // when
    try (final var scan = new ViewSource<>(store.backend(), store.openView(ALL))) {
      // then
      assertThat(forward(scan)).containsExactly(2L);
      assertThat(readValue(scan, key(2))).isEqualTo(20);
    }
  }

  @Test
  void shouldWalkBackwardOverTheSameState() {
    // given
    commit(store, key(1), held(1));
    commit(store, key(2), held(2));

    // when
    try (final var scan = new ViewSource<>(store.backend(), store.openView(ALL))) {
      commit(store, key(3), held(3));
      commit(store, key(2), null);

      // then
      final List<Long> keys = new ArrayList<>();
      scan.seekForPrev(key(99));
      while (scan.isValid()) {
        keys.add(idOf(scan.key()));
        scan.advance();
      }
      assertThat(keys).containsExactly(2L, 1L);
    }
  }

  private static List<Long> forward(final ViewSource<RawKey, HeldValue, DbValue> scan) {
    final List<Long> keys = new ArrayList<>();
    scan.seek(key(0));
    while (scan.isValid()) {
      keys.add(idOf(scan.key()));
      scan.advance();
    }
    return keys;
  }

  private static long readValue(
      final ViewSource<RawKey, HeldValue, DbValue> scan, final RawKey key) {
    final var value = new DbLong();
    scan.seek(key);
    assertThat(scan.readValueUnlessStale(value)).isTrue();
    return value.getValue();
  }

  private static long idOf(final RawKey key) {
    return java.nio.ByteBuffer.wrap(key.bytes())
        .order(io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER)
        .getLong(Long.BYTES);
  }
}
