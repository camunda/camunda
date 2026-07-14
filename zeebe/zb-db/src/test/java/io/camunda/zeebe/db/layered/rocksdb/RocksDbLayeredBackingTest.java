/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.layered.BytesStore;
import io.camunda.zeebe.db.layered.PersistBatch;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocksDbLayeredBackingTest {

  private static final String STORE_A = "store-a";
  private static final String STORE_B = "store-b";

  private @TempDir Path tempDir;
  private RocksDbLayeredBacking backing;

  @BeforeEach
  void setUp() {
    backing = RocksDbLayeredBacking.open(tempDir, List.of(STORE_A, STORE_B));
  }

  @AfterEach
  void tearDown() {
    if (backing != null) {
      backing.close();
    }
  }

  @Test
  void shouldRoundTripPutGetDelete() {
    // given
    final BytesStore store = backing.store(STORE_A);
    final byte[] key = bytes(1, 2, 3);
    final byte[] value = bytes(4, 5, 6);

    // when
    store.put(key, value);

    // then
    assertThat(store.get(key)).containsExactly(value);
    assertThat(store.get(bytes(9, 9))).isNull();

    // when
    store.delete(key);

    // then
    assertThat(store.get(key)).isNull();
  }

  @Test
  void shouldIsolateStoresByName() {
    // given
    final BytesStore storeA = backing.store(STORE_A);
    final BytesStore storeB = backing.store(STORE_B);
    final byte[] sharedKey = bytes(42);

    // when - the same key gets a different value per store
    storeA.put(sharedKey, bytes(1));
    storeB.put(sharedKey, bytes(2));

    // then
    assertThat(storeA.get(sharedKey)).containsExactly(bytes(1));
    assertThat(storeB.get(sharedKey)).containsExactly(bytes(2));

    // when - deleting in one store
    storeA.delete(sharedKey);

    // then - the other store is untouched
    assertThat(storeA.get(sharedKey)).isNull();
    assertThat(storeB.get(sharedKey)).containsExactly(bytes(2));
  }

  @Test
  void shouldRejectUnknownStoreName() {
    // when / then
    assertThatThrownBy(() -> backing.store("no-such-store"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no-such-store");
  }

  @Test
  void shouldRejectUnknownStoreNameInBatch() {
    // given
    try (final PersistBatch batch = backing.sink().newBatch()) {
      // when / then
      assertThatThrownBy(() -> batch.put("no-such-store", bytes(1), bytes(2)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("no-such-store");
    }
  }

  @Test
  void shouldScanAllEntriesInUnsignedOrderForEmptyPrefix() {
    // given - keys inserted out of order, including a high-bit key that would sort before 0x7F
    // under signed comparison
    final BytesStore store = backing.store(STORE_A);
    store.put(bytes(0x80), bytes(3));
    store.put(bytes(0x01), bytes(1));
    store.put(bytes(0x7F), bytes(2));

    // when
    final List<byte[]> keys = new ArrayList<>();
    store.prefixScan(new byte[0], (key, value) -> keys.add(key));

    // then
    assertThat(keys).containsExactly(bytes(0x01), bytes(0x7F), bytes(0x80));
  }

  @Test
  void shouldScanOnlyKeysStartingWithPrefix() {
    // given - neighbors just below and above the prefix range, plus 0x00 and 0xFF tails inside it
    final BytesStore store = backing.store(STORE_A);
    store.put(bytes(0x00, 0xFF), bytes(0));
    store.put(bytes(0x01), bytes(1));
    store.put(bytes(0x01, 0x00), bytes(2));
    store.put(bytes(0x01, 0xFF), bytes(3));
    store.put(bytes(0x02), bytes(4));

    // when
    final List<byte[]> keys = new ArrayList<>();
    store.prefixScan(bytes(0x01), (key, value) -> keys.add(key));

    // then
    assertThat(keys).containsExactly(bytes(0x01), bytes(0x01, 0x00), bytes(0x01, 0xFF));
  }

  @Test
  void shouldScanNothingForAbsentPrefix() {
    // given
    final BytesStore store = backing.store(STORE_A);
    store.put(bytes(0x01), bytes(1));
    store.put(bytes(0x03), bytes(3));

    // when
    final List<byte[]> keys = new ArrayList<>();
    store.prefixScan(bytes(0x02), (key, value) -> keys.add(key));

    // then
    assertThat(keys).isEmpty();
  }

  @Test
  void shouldCommitBatchAtomicallyAcrossStoresAndAnchor() throws Exception {
    // given - a pre-existing key that the batch deletes, plus puts in two stores and the anchor
    final BytesStore storeA = backing.store(STORE_A);
    final BytesStore storeB = backing.store(STORE_B);
    storeA.put(bytes(0x0A), bytes(1));

    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.put(STORE_A, bytes(0x0B), bytes(2));
      batch.put(STORE_B, bytes(0x0C), bytes(3));
      batch.delete(STORE_A, bytes(0x0A));
      batch.putAnchor(42L);

      // then - nothing staged is visible before commit
      assertThat(storeA.get(bytes(0x0B))).isNull();
      assertThat(storeB.get(bytes(0x0C))).isNull();
      assertThat(storeA.get(bytes(0x0A))).containsExactly(bytes(1));
      assertThat(backing.sink().readAnchor()).isEqualTo(-1);

      // when
      batch.commit();
    }

    // then - everything is visible after commit
    assertThat(storeA.get(bytes(0x0B))).containsExactly(bytes(2));
    assertThat(storeB.get(bytes(0x0C))).containsExactly(bytes(3));
    assertThat(storeA.get(bytes(0x0A))).isNull();
    assertThat(backing.sink().readAnchor()).isEqualTo(42L);
  }

  @Test
  void shouldApplyNothingWhenBatchClosedWithoutCommit() {
    // given
    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.put(STORE_A, bytes(0x0D), bytes(4));
      batch.putAnchor(7L);
      // when - closed without commit
    }

    // then
    assertThat(backing.store(STORE_A).get(bytes(0x0D))).isNull();
    assertThat(backing.sink().readAnchor()).isEqualTo(-1);
  }

  @Test
  void shouldRejectSecondAnchorInSameBatch() {
    // given
    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.putAnchor(1L);

      // when / then
      assertThatThrownBy(() -> batch.putAnchor(2L)).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void shouldReadMinusOneAnchorOnFreshDatabase() {
    // when / then
    assertThat(backing.sink().readAnchor()).isEqualTo(-1);
  }

  @Test
  void shouldAdvanceAnchorAcrossBatches() throws Exception {
    // given
    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.putAnchor(5L);
      batch.commit();
    }

    // when
    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.putAnchor(9L);
      batch.commit();
    }

    // then
    assertThat(backing.sink().readAnchor()).isEqualTo(9L);
  }

  @Test
  void shouldRecoverAnchorAndStateAfterReopen() throws Exception {
    // given - a committed cut of state plus anchor
    try (final PersistBatch batch = backing.sink().newBatch()) {
      batch.put(STORE_A, bytes(0x0E), bytes(5));
      batch.putAnchor(1337L);
      batch.commit();
    }

    // when - the database is closed and reopened at the same path
    backing.close();
    backing = RocksDbLayeredBacking.open(tempDir, List.of(STORE_A, STORE_B));

    // then - the recovery path sees the anchored cut
    assertThat(backing.sink().readAnchor()).isEqualTo(1337L);
    assertThat(backing.store(STORE_A).get(bytes(0x0E))).containsExactly(bytes(5));
  }

  @Test
  void shouldServePinnedStateFromSnapshot() throws Exception {
    // given - committed state, then a snapshot pinned on it
    final BytesStore storeA = backing.store(STORE_A);
    storeA.put(bytes(0x10), bytes(1));

    try (final ReadSnapshot snapshot = backing.snapshotSource().takeSnapshot()) {
      // when - a batch commits an overwrite and a new key after the snapshot was taken
      try (final PersistBatch batch = backing.sink().newBatch()) {
        batch.put(STORE_A, bytes(0x10), bytes(2));
        batch.put(STORE_A, bytes(0x11), bytes(3));
        batch.commit();
      }

      // then - the snapshot still serves the pinned state, for gets and scans alike
      assertThat(snapshot.get(STORE_A, bytes(0x10))).containsExactly(bytes(1));
      assertThat(snapshot.get(STORE_A, bytes(0x11))).isNull();
      final List<byte[]> snapshotKeys = new ArrayList<>();
      final List<byte[]> snapshotValues = new ArrayList<>();
      snapshot.prefixScan(
          STORE_A,
          new byte[0],
          (key, value) -> {
            snapshotKeys.add(key);
            snapshotValues.add(value);
          });
      assertThat(snapshotKeys).containsExactly(bytes(0x10));
      assertThat(snapshotValues).containsExactly(bytes(1));

      // then - the live store sees the new state
      assertThat(storeA.get(bytes(0x10))).containsExactly(bytes(2));
      assertThat(storeA.get(bytes(0x11))).containsExactly(bytes(3));
    }

    // when - a new snapshot is taken after the old one was closed
    try (final ReadSnapshot freshSnapshot = backing.snapshotSource().takeSnapshot()) {
      // then - it sees the new state
      assertThat(freshSnapshot.get(STORE_A, bytes(0x10))).containsExactly(bytes(2));
      assertThat(freshSnapshot.get(STORE_A, bytes(0x11))).containsExactly(bytes(3));
    }
  }

  @Test
  void shouldTolerateClosingSnapshotTwice() {
    // given
    final ReadSnapshot snapshot = backing.snapshotSource().takeSnapshot();
    snapshot.close();

    // when / then
    assertThatCode(snapshot::close).doesNotThrowAnyException();
  }

  @Test
  void shouldTolerateClosingBackingTwice() {
    // given
    backing.close();

    // when / then
    assertThatCode(backing::close).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectReservedStoreNamesOnOpen(final @TempDir Path freshDir) {
    // when / then
    assertThatThrownBy(
            () ->
                RocksDbLayeredBacking.open(
                    freshDir, List.of(RocksDbLayeredBacking.ANCHOR_COLUMN_FAMILY)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] bytes(final int... values) {
    final byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }
}
