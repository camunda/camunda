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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

final class WindowedIndexTest {

  private static final long CF = 7;
  private static final int CAP = 10;

  private CountingRawDbAccess access;
  private WriteBehindStore store;
  private WriteBehindTransaction transaction;

  @AfterEach
  void tearDown() {
    store.close();
  }

  private void open(final KeyIndexPolicy policy) {
    access = new CountingRawDbAccess(new InMemoryRawDbAccess());
    store =
        new WriteBehindStore(
            access,
            WriteBehindConfiguration.defaults()
                .withCacheMaxBytes(0)
                .withKeyIndexColumnFamilies(Set.of(CF))
                .withKeyIndexMaxKeys(CAP)
                .withKeyIndexPolicies(Map.of(CF, policy)));
    transaction = new WriteBehindTransaction(store);
    transaction.resetTransaction();
  }

  @Test
  void shouldLoadLowestKeysAndBoundAtFirstKeyLeftOut() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30));

    // when
    store.prepareKeyIndex(CF);

    // then
    assertThat(indexed()).containsExactlyElementsOf(range(1, 10));
    assertThat(store.keyIndex(CF).bound()).isEqualTo(key(11));
  }

  @Test
  void shouldLoadHighestKeysAndBoundAtFirstKeyLeftOut() {
    // given
    open(KeyIndexPolicy.KEEP_HIGHEST);
    put(LongStream.rangeClosed(1, 30));

    // when
    store.prepareKeyIndex(CF);

    // then
    assertThat(indexed()).containsExactlyElementsOf(range(21, 30));
    assertThat(store.keyIndex(CF).bound()).isEqualTo(key(20));
  }

  @Test
  void shouldCoverWholeColumnFamilyThatFitsTheCap() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, CAP));

    // when
    store.prepareKeyIndex(CF);

    // then
    assertThat(store.keyIndex(CF).bound()).isNull();
    assertThat(indexed()).hasSize(CAP);
  }

  @Test
  void shouldHandOverToStorageAtBoundInForwardScanOfLowestWindow() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30));
    store.prepareKeyIndex(CF);
    final long iterators = store.metrics().iteratorsOpened.sum();

    // when
    final var ids = scan(true, 0);

    // then
    assertThat(ids).containsExactlyElementsOf(range(1, 30));
    assertThat(store.metrics().keyIndexHandovers.sum()).isEqualTo(1);
    assertThat(store.metrics().iteratorsOpened.sum()).isEqualTo(iterators + 1);
    assertThat(store.metrics().keyIndexSwitches.sum()).isZero();
  }

  @Test
  void shouldHandOverToStorageAtBoundInReverseScanOfHighestWindow() {
    // given
    open(KeyIndexPolicy.KEEP_HIGHEST);
    put(LongStream.rangeClosed(1, 30));
    store.prepareKeyIndex(CF);

    // when
    final var ids = scan(false, 100);

    // then
    assertThat(ids).containsExactlyElementsOf(range(1, 30).reversed());
    assertThat(store.metrics().keyIndexHandovers.sum()).isEqualTo(1);
  }

  @Test
  void shouldUseStorageForScanThatStartsOutsideTheWindow() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30));
    store.prepareKeyIndex(CF);

    // when
    final var ids = scan(true, 15);

    // then
    assertThat(ids).containsExactlyElementsOf(range(15, 30));
    assertThat(store.metrics().indexedScans.sum()).isZero();
    assertThat(store.metrics().keyIndexHandovers.sum()).isZero();
  }

  @Test
  void shouldOpenNoIteratorForReverseScanInsideLowestWindow() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30));
    store.prepareKeyIndex(CF);
    final long iterators = store.metrics().iteratorsOpened.sum();

    // when
    final var ids = scan(false, 5);

    // then
    assertThat(ids).containsExactlyElementsOf(range(1, 5).reversed());
    assertThat(store.metrics().iteratorsOpened.sum()).isEqualTo(iterators);
  }

  @Test
  void shouldEvictFarKeyAndMoveBoundWhenInsertBelowBoundExceedsCap() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30));
    store.prepareKeyIndex(CF);

    // when
    put(LongStream.of(0));

    // then
    assertThat(indexed()).containsExactlyElementsOf(range(0, 9));
    assertThat(store.keyIndex(CF).bound()).isEqualTo(key(10));
    assertThat(scan(true, 0)).containsExactlyElementsOf(range(0, 30));
  }

  @Test
  void shouldIgnoreInsertOutsideTheWindow() {
    // given
    open(KeyIndexPolicy.KEEP_HIGHEST);
    put(LongStream.rangeClosed(11, 30));
    store.prepareKeyIndex(CF);

    // when
    put(LongStream.of(1));

    // then
    assertThat(indexed()).containsExactlyElementsOf(range(21, 30));
    assertThat(store.keyIndex(CF).bound()).isEqualTo(key(20));
    assertThat(scan(true, 0)).containsExactlyElementsOf(join(List.of(1L), range(11, 30)));
  }

  @Test
  void shouldAnswerAbsentKeyOnlyInsideTheWindow() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 30).filter(id -> id != 5 && id != 25));
    store.awaitFlushed();
    store.prepareKeyIndex(CF);
    final long gets = access.gets();

    // when
    final boolean absentInside = store.exists(key(5));
    final long afterInside = access.gets();
    final boolean absentOutside = store.exists(key(25));

    // then
    assertThat(absentInside).isFalse();
    assertThat(afterInside).isEqualTo(gets);
    assertThat(absentOutside).isFalse();
    assertThat(access.gets()).isEqualTo(afterInside + 1);
  }

  @ParameterizedTest
  @EnumSource(
      value = KeyIndexPolicy.class,
      names = {"KEEP_LOWEST", "KEEP_HIGHEST"})
  void shouldExtendDrainedWindowAwayFromCoveredEnd(final KeyIndexPolicy policy) {
    // given
    open(policy);
    put(LongStream.rangeClosed(1, 40));
    store.prepareKeyIndex(CF);
    final boolean lowest = policy == KeyIndexPolicy.KEEP_LOWEST;
    // leaves one key in the window; CAP / 4 = 2
    for (long id = lowest ? 1 : 40; lowest ? id < 10 : id > 31; id += lowest ? 1 : -1) {
      delete(id);
    }
    assertThat(store.keyIndex(CF).size()).isEqualTo(1);

    // when
    store.prepareKeyIndex(CF);

    // then
    assertThat(store.metrics().keyIndexRefills.sum()).isEqualTo(1);
    assertThat(indexed()).hasSize(3);
    final var expected = new TreeSet<>(range(lowest ? 10 : 1, lowest ? 40 : 31));
    assertThat(scan(true, 0)).containsExactlyElementsOf(expected);
    assertThat(scan(false, 100)).containsExactlyElementsOf(expected.descendingSet());
  }

  @Test
  void shouldDisableAfterThreeDiscardedRefills() {
    // given
    open(KeyIndexPolicy.KEEP_LOWEST);
    put(LongStream.rangeClosed(1, 40));
    store.prepareKeyIndex(CF);
    for (long id = 1; id < 10; id++) {
      delete(id);
    }

    // when
    for (long i = 0; i < 3; i++) {
      final long id = 100 + i;
      // a commit that lands between the refill's view and its install makes the refill obsolete
      access.afterNextIteratorClosed(() -> put(LongStream.of(id)));
      store.prepareKeyIndex(CF);
    }

    // then
    assertThat(store.keyIndex(CF).state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(store.metrics().keyIndexDiscardedRefills.sum()).isEqualTo(3);
    assertThat(scan(true, 0))
        .containsExactlyElementsOf(join(range(10, 40), List.of(100L, 101L, 102L)));
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
  void shouldKeepWindowInvariantsAndMatchModelThroughRandomOperations(final long seed) {
    // given
    final var policy = seed % 2 == 0 ? KeyIndexPolicy.KEEP_LOWEST : KeyIndexPolicy.KEEP_HIGHEST;
    open(policy);
    final var random = new Random(seed);
    final var model = new TreeSet<Long>();
    for (int i = 0; i < 25; i++) {
      final long id = random.nextInt(60);
      model.add(id);
      put(LongStream.of(id));
    }
    store.prepareKeyIndex(CF);

    for (int step = 0; step < 300; step++) {
      // when
      final long id = random.nextInt(60);
      if (random.nextInt(3) == 0) {
        model.remove(id);
        delete(id);
      } else {
        model.add(id);
        put(LongStream.of(id));
      }
      if (random.nextInt(5) == 0) {
        store.awaitFlushed();
      }
      store.prepareKeyIndex(CF);

      // then
      final var index = store.keyIndex(CF);
      if (index.state() == KeyIndex.State.LOADED) {
        final var bound = index.bound();
        final var expected = model.stream().filter(m -> index.covers(bound, key(m))).toList();
        assertThat(indexed()).containsExactlyElementsOf(expected);
        assertThat(index.size()).isLessThanOrEqualTo(CAP);
      }
      if (step % 10 == 0) {
        assertThat(scan(true, 0)).containsExactlyElementsOf(model);
        assertThat(scan(false, 100)).containsExactlyElementsOf(model.descendingSet());
      }
    }
  }

  /** The keys of a scan. It reads no values, so the index source never turns stale on reads. */
  private List<Long> scan(final boolean forward, final long from) {
    transaction.prepareScan(CF);
    final var ids = new ArrayList<Long>();
    try (final var cursor = transaction.newCursor(prefix())) {
      if (forward) {
        cursor.seek(from == 0 ? prefix() : key(from).bytes());
      } else {
        cursor.seekForPrev(key(from).bytes());
      }
      while (cursor.isValid()) {
        ids.add(ByteBuffer.wrap(cursor.key()).getLong(Long.BYTES));
        cursor.advance();
      }
    }
    return ids;
  }

  private List<Long> indexed() {
    return store.keyIndex(CF).keys().stream()
        .map(key -> ByteBuffer.wrap(key.bytes()).getLong(Long.BYTES))
        .toList();
  }

  private static List<Long> range(final long from, final long to) {
    return LongStream.rangeClosed(from, to).boxed().toList();
  }

  private static List<Long> join(final List<Long> first, final List<Long> second) {
    final var all = new ArrayList<>(first);
    all.addAll(second);
    return all;
  }

  private void put(final LongStream ids) {
    ids.forEach(
        id -> {
          final var value = new DbLong();
          value.wrapLong(id);
          store.commit(new RawKey[] {key(id)}, new DbValue[] {value});
        });
  }

  private void delete(final long id) {
    store.commit(new RawKey[] {key(id)}, new DbValue[1]);
  }

  private static byte[] prefix() {
    return ByteBuffer.allocate(Long.BYTES)
        .order(ZeebeDbConstants.ZB_DB_BYTE_ORDER)
        .putLong(CF)
        .array();
  }

  private static RawKey key(final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(CF).putLong(id).array());
  }
}
