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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class IndexedScanTest {

  private static final long INDEXED = 7;
  private static final long NOT_INDEXED = 11;

  private CountingRawDbAccess access;
  private WriteBehindStore<RawKey, DbValue> store;
  private WriteBehindTransaction transaction;

  @BeforeEach
  void setUp() {
    open(
        WriteBehindConfiguration.defaults()
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexColumnFamilies(Set.of(INDEXED)));
  }

  @AfterEach
  void tearDown() throws Exception {
    store.close();
  }

  private void open(final WriteBehindConfiguration config) {
    access = new CountingRawDbAccess(new InMemoryRawDbAccess());
    store = new WriteBehindStore<>(new RawBackend(access), config);
    transaction = new WriteBehindTransaction(store);
    transaction.resetTransaction();
  }

  private void reopen(final WriteBehindConfiguration config) throws Exception {
    tearDown();
    open(config);
  }

  @Test
  void shouldOpenNoIteratorOnceLoaded() {
    // given
    put(INDEXED, 1, 2, 3);
    scan(INDEXED);
    final long iterators = store.metrics().iteratorsOpened.sum();

    // when
    final var result = scan(INDEXED);

    // then
    assertThat(result).containsExactly(Map.entry(1L, 1L), Map.entry(2L, 2L), Map.entry(3L, 3L));
    assertThat(store.metrics().iteratorsOpened.sum()).isEqualTo(iterators);
    assertThat(store.metrics().indexedScans.sum()).isEqualTo(2);
  }

  @Test
  void shouldSeeCommitsAfterLoad() {
    // given
    put(INDEXED, 1, 2, 3);
    scan(INDEXED);

    // when
    put(INDEXED, 4);
    putValue(INDEXED, 2, 20);
    delete(INDEXED, 1);

    // then
    assertThat(scan(INDEXED))
        .containsExactly(Map.entry(2L, 20L), Map.entry(3L, 3L), Map.entry(4L, 4L));
    assertThat(store.metrics().keyIndexSwitches.sum()).isZero();
  }

  @Test
  void shouldUseStorageIteratorForColumnFamilyThatIsNotConfigured() {
    // given
    put(NOT_INDEXED, 1, 2);

    // when
    scan(NOT_INDEXED);
    scan(NOT_INDEXED);

    // then
    assertThat(store.keyIndex(NOT_INDEXED)).isNull();
    assertThat(store.metrics().iteratorsOpened.sum()).isEqualTo(2);
    assertThat(store.metrics().indexedScans.sum()).isZero();
  }

  @Test
  void shouldNotChangeVersionOrSwitchWhenExistingKeyIsUpdatedDuringScan() {
    // given
    put(INDEXED, 1, 2, 3);
    final var index = store.keyIndex(INDEXED);
    final var cursor = cursor(INDEXED);
    cursor.seek(RawKey.of(prefix(INDEXED)));
    final long version = index.version();

    // when
    putValue(INDEXED, 2, 20); // from a second context
    final var ids = drain(cursor);

    // then
    assertThat(index.version()).isEqualTo(version);
    assertThat(ids).containsExactly(Map.entry(1L, 1L), Map.entry(2L, 20L), Map.entry(3L, 3L));
    assertThat(store.metrics().keyIndexSwitches.sum()).isZero();
  }

  @Test
  void shouldAnswerAbsentKeyWithoutStorageRead() {
    // given
    put(INDEXED, 1, 2);
    store.awaitFlushed();
    store.prepareKeyIndex(INDEXED);
    final long gets = access.gets();

    // then
    assertThat(store.exists(key(INDEXED, 99))).isFalse();
    assertThat(store.get(key(INDEXED, 99), new DbLong())).isNull();
    assertThat(access.gets()).isEqualTo(gets);

    // and a key of the index is not answered as absent
    assertThat(store.exists(key(INDEXED, 1))).isTrue();
  }

  @Test
  void shouldLoadIndexOnFirstPointReadAndAnswerAbsentAfterwards() {
    // given an index that no scan has loaded
    put(INDEXED, 1);
    store.awaitFlushed();

    // when
    final boolean first = store.exists(key(INDEXED, 99));
    final long gets = access.gets();
    final boolean second = store.exists(key(INDEXED, 98));

    // then the first read loaded the index, so the second needs no storage read
    assertThat(first).isFalse();
    assertThat(second).isFalse();
    assertThat(access.gets()).isEqualTo(gets);
  }

  @Test
  void shouldSwitchWhenKeysChangeBetweenSteps() {
    // given
    put(INDEXED, 10, 20, 30, 40, 50);
    final var cursor = cursor(INDEXED);
    cursor.seek(RawKey.of(prefix(INDEXED)));
    final var seen = new ArrayList<Long>();
    seen.add(id(cursor));

    // when a key that the cursor didn't reach yet is replaced
    delete(INDEXED, 20);
    put(INDEXED, 25);
    while (true) {
      cursor.advance();
      if (!cursor.isValid()) {
        break;
      }
      seen.add(id(cursor));
    }

    // then the view that the cursor switched to has the committed state of that moment
    assertThat(seen).containsExactly(10L, 25L, 30L, 40L, 50L);
    assertThat(store.metrics().keyIndexSwitches.sum()).isEqualTo(1);
    cursor.close();
  }

  @Test
  void shouldContinueAfterKeyUnderTheCursorIsDeleted() {
    // given
    put(INDEXED, 10, 20, 30);
    final var cursor = cursor(INDEXED);
    cursor.seek(RawKey.of(prefix(INDEXED)));
    cursor.advance();
    assertThat(id(cursor)).isEqualTo(20);

    // when
    delete(INDEXED, 20);
    cursor.advance();

    // then
    assertThat(id(cursor)).isEqualTo(30);
    assertThat(store.metrics().keyIndexSwitches.sum()).isEqualTo(1);
    cursor.close();
  }

  @Test
  void shouldSkipEntryThatIsDeletedWhileItsValueIsRead() throws Exception {
    // given
    reopen(
        WriteBehindConfiguration.defaults()
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexPolicies(Map.of())
            .withCacheMaxBytes(0)
            .withKeyIndexColumnFamilies(Set.of(INDEXED)));
    put(INDEXED, 1, 2, 3);
    store.awaitFlushed();
    final var cursor = cursor(INDEXED);
    cursor.seek(RawKey.of(prefix(INDEXED)));
    final var target = new DbLong();

    // when
    delete(INDEXED, 1);
    final boolean read = cursor.readValue(target);

    // then
    assertThat(read).isFalse();
    cursor.advance();
    assertThat(id(cursor)).isEqualTo(2);
    assertThat(cursor.readValue(target)).isTrue();
    assertThat(target.getValue()).isEqualTo(2);
    cursor.close();
  }

  @Test
  void shouldSwitchToIteratorAfterFourStorageReads() throws Exception {
    // given
    reopen(
        WriteBehindConfiguration.defaults()
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexPolicies(Map.of())
            .withCacheMaxBytes(0)
            .withKeyIndexColumnFamilies(Set.of(INDEXED)));
    put(INDEXED, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    store.awaitFlushed();
    store.prepareKeyIndex(INDEXED);
    final long gets = access.gets();
    final long iterators = access.iterators();

    // when
    final var result = scan(INDEXED);

    // then
    assertThat(result).hasSize(10);
    result.forEach((id, value) -> assertThat(value).isEqualTo(id));
    assertThat(access.gets() - gets).isEqualTo(IndexSource.MAX_STORAGE_READS);
    assertThat(access.iterators() - iterators).isEqualTo(1);
    assertThat(store.metrics().keyIndexSwitches.sum()).isEqualTo(1);
  }

  @Test
  void shouldScanInReverse() {
    // given
    put(INDEXED, 1, 2, 3, 4);
    final var cursor = cursor(INDEXED);

    // when
    cursor.seekForPrev(key(INDEXED, 3));
    final var seen = new ArrayList<Long>();
    while (cursor.isValid()) {
      seen.add(id(cursor));
      if (seen.size() == 1) {
        put(INDEXED, 0); // changes the key set in the middle of the scan
      }
      cursor.advance();
      if (cursor.isValid() && id(cursor) == 0) {
        // the cursor stays inside the column family
        seen.add(id(cursor));
        break;
      }
    }

    // then
    assertThat(seen).containsExactly(3L, 2L, 1L, 0L);
    assertThat(store.metrics().keyIndexSwitches.sum()).isEqualTo(1);
    cursor.close();
  }

  @Test
  void shouldMergeOwnWritesLiveDuringIndexedScan() {
    // given
    put(INDEXED, 1, 3, 5);
    scan(INDEXED);
    final var cursor = cursor(INDEXED);

    // when
    cursor.seek(RawKey.of(prefix(INDEXED)));
    final var seen = new ArrayList<Long>();
    while (cursor.isValid()) {
      final long id = id(cursor);
      seen.add(id);
      if (id == 1) {
        transaction.put(key(INDEXED, 2), value(2)); // ahead of the cursor
      }
      transaction.delete(key(INDEXED, id));
      cursor.advance();
    }

    // then
    assertThat(seen).containsExactly(1L, 2L, 3L, 5L);
    assertThat(drain(cursor(INDEXED))).isEmpty();
    cursor.close();
  }

  @Test
  void shouldHideCommittedKeysThatThisTransactionDeletes() {
    // given
    put(INDEXED, 1, 2, 3);
    scan(INDEXED);

    // when
    transaction.delete(key(INDEXED, 2));
    transaction.put(key(INDEXED, 4), value(4));

    // then
    assertThat(scan(INDEXED)).containsOnlyKeys(1L, 3L, 4L);
  }

  @Test
  void shouldScanCorrectlyAfterIndexWasDisabled() throws Exception {
    // given
    reopen(
        WriteBehindConfiguration.defaults()
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexColumnFamilies(Set.of(INDEXED))
            .withKeyIndexMaxKeys(2));
    put(INDEXED, 1, 2);
    scan(INDEXED);

    // when
    put(INDEXED, 3);

    // then
    assertThat(store.keyIndex(INDEXED).state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(scan(INDEXED)).containsOnlyKeys(1L, 2L, 3L);
    assertThat(store.metrics().indexedScans.sum()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6})
  void shouldNotRepeatOrSkipKeysWhileAnotherContextChangesTheColumnFamily(final long seed)
      throws Exception {
    // given
    // stable keys are never touched, volatile keys come and go between the steps
    reopen(
        WriteBehindConfiguration.defaults()
            .withKeyIndexPolicies(Map.of())
            .withKeyIndexPolicies(Map.of())
            .withCacheMaxBytes(seed % 2 == 0 ? 0 : 1 << 20)
            .withKeyIndexColumnFamilies(Set.of(INDEXED)));
    final var random = new Random(seed);
    final var stable = new TreeSet<Long>();
    for (long id = 0; id < 40; id += 2) {
      stable.add(id);
      put(INDEXED, id);
    }
    for (long id = 1; id < 40; id += 2) {
      if (random.nextBoolean()) {
        put(INDEXED, id);
      }
    }
    store.awaitFlushed();
    scan(INDEXED);

    for (final boolean forward : new boolean[] {true, false}) {
      final var cursor = cursor(INDEXED);
      if (forward) {
        cursor.seek(RawKey.of(prefix(INDEXED)));
      } else {
        cursor.seekForPrev(key(INDEXED, 100));
      }

      // when
      final var seen = new ArrayList<Long>();
      while (cursor.isValid()) {
        final long id = id(cursor);
        final var value = new DbLong();
        if (cursor.readValue(value)) {
          assertThat(value.getValue()).isEqualTo(id);
          seen.add(id);
        }
        // between two steps a second context inserts or deletes volatile keys
        final long volatileId = 1 + 2L * random.nextInt(20);
        if (random.nextBoolean()) {
          put(INDEXED, volatileId);
        } else {
          delete(INDEXED, volatileId);
        }
        if (random.nextInt(4) == 0) {
          store.awaitFlushed();
        }
        cursor.advance();
      }
      cursor.close();

      // then
      assertThat(seen).doesNotHaveDuplicates();
      assertThat(seen).isSortedAccordingTo(forward ? Long::compare : (a, b) -> Long.compare(b, a));
      assertThat(seen).containsAll(stable);
      assertThat(seen).allMatch(id -> id >= 0 && id < 40);
    }
  }

  private MergingCursor<RawKey, DbValue> cursor(final long columnFamily) {
    transaction.prepareScan(columnFamily);
    return transaction.newCursor(prefix(columnFamily));
  }

  /** Reads the whole column family with values, in order. */
  private Map<Long, Long> scan(final long columnFamily) {
    final var cursor = cursor(columnFamily);
    cursor.seek(RawKey.of(prefix(columnFamily)));
    return drain(cursor);
  }

  private Map<Long, Long> drain(final MergingCursor<RawKey, DbValue> cursor) {
    final var result = new LinkedHashMap<Long, Long>();
    try (cursor) {
      while (cursor.isValid()) {
        final var value = new DbLong();
        if (cursor.readValue(value)) {
          result.put(id(cursor), value.getValue());
        }
        cursor.advance();
      }
    }
    return result;
  }

  private static long id(final MergingCursor<RawKey, DbValue> cursor) {
    return ByteBuffer.wrap(cursor.key().bytes()).getLong(Long.BYTES);
  }

  private static byte[] prefix(final long columnFamily) {
    return ByteBuffer.allocate(Long.BYTES)
        .order(ZeebeDbConstants.ZB_DB_BYTE_ORDER)
        .putLong(columnFamily)
        .array();
  }

  private void put(final long columnFamily, final long... ids) {
    for (final long id : ids) {
      putValue(columnFamily, id, id);
    }
  }

  private void putValue(final long columnFamily, final long id, final long value) {
    store.commit(new RawKey[] {key(columnFamily, id)}, new DbValue[] {value(value)});
  }

  private void delete(final long columnFamily, final long id) {
    store.commit(new RawKey[] {key(columnFamily, id)}, new DbValue[1]);
  }

  private static RawKey key(final long columnFamily, final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(columnFamily).putLong(id).array());
  }

  private static DbLong value(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }
}
