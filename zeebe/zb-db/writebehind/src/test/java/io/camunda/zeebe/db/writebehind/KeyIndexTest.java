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
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class KeyIndexTest {

  private static final long INDEXED = 7;
  private static final long OTHER = 9;
  private static final long NOT_INDEXED = 11;

  private CountingRawDbAccess access;
  private WriteBehindStore store;

  @BeforeEach
  void setUp() {
    open(config(1000));
  }

  @AfterEach
  void tearDown() throws Exception {
    store.close();
  }

  private void open(final WriteBehindConfiguration config) {
    access = new CountingRawDbAccess(new InMemoryRawDbAccess());
    store = new WriteBehindStore(access, config);
  }

  private void reopen(final WriteBehindConfiguration config) throws Exception {
    tearDown();
    open(config);
  }

  private static WriteBehindConfiguration config(final int maxKeys) {
    return WriteBehindConfiguration.defaults()
        .withKeyIndexColumnFamilies(Set.of(INDEXED, OTHER))
        .withKeyIndexMaxKeys(maxKeys);
  }

  @Test
  void shouldLoadFlushedAndUnflushedKeysOnly() {
    // given
    put(INDEXED, 1, 2, 3);
    store.awaitFlushed();
    put(INDEXED, 4);
    delete(INDEXED, 2);
    put(NOT_INDEXED, 100);

    // when
    store.loadKeyIndexIfNeeded(INDEXED);

    // then
    assertThat(indexed(INDEXED)).containsExactly(1L, 3L, 4L);
    assertThat(store.keyIndex(INDEXED).state()).isEqualTo(KeyIndex.State.LOADED);
    assertThat(store.metrics().keyIndexLoads.sum()).isEqualTo(1);
  }

  @Test
  void shouldNotCreateIndexForColumnFamilyThatIsNotConfigured() {
    // when
    store.loadKeyIndexIfNeeded(NOT_INDEXED);

    // then
    assertThat(store.keyIndex(NOT_INDEXED)).isNull();
    assertThat(store.metrics().iteratorsOpened.sum()).isZero();
  }

  @Test
  void shouldKeepLoadedIndexCurrentThroughCommits() {
    // given
    put(INDEXED, 1, 2);
    store.loadKeyIndexIfNeeded(INDEXED);

    // when
    put(INDEXED, 3);
    delete(INDEXED, 1);
    put(INDEXED, 2); // update of an existing key

    // then
    assertThat(indexed(INDEXED)).containsExactly(2L, 3L);
    assertThat(committedKeys(INDEXED)).containsExactly(2L, 3L);
  }

  @Test
  void shouldNotChangeVersionWhenUpdatingExistingKey() {
    // given
    put(INDEXED, 1, 2);
    store.loadKeyIndexIfNeeded(INDEXED);
    final var index = store.keyIndex(INDEXED);
    final long before = index.version();

    // when
    put(INDEXED, 1);
    delete(INDEXED, 5); // a delete of an absent key

    // then
    assertThat(index.version()).isEqualTo(before);
    assertThat(index.isChanging()).isFalse();
  }

  @Test
  void shouldChangeVersionByTwoPerCommitThatChangesKeys() {
    // given
    store.loadKeyIndexIfNeeded(INDEXED);
    final var index = store.keyIndex(INDEXED);
    final long before = index.version();

    // when
    put(INDEXED, 1, 2, 3);

    // then
    assertThat(index.version()).isEqualTo(before + 2);
  }

  @Test
  void shouldDisableWhenLoadExceedsCap() throws Exception {
    // given
    reopen(config(3));
    put(INDEXED, 1, 2, 3, 4);

    // when
    store.loadKeyIndexIfNeeded(INDEXED);

    // then
    final var index = store.keyIndex(INDEXED);
    assertThat(index.state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(index.keys()).isEmpty();
    assertThat(store.metrics().keyIndexDisables.sum()).isEqualTo(1);
  }

  @Test
  void shouldLoadWhenKeyCountEqualsCap() throws Exception {
    // given
    reopen(config(3));
    put(INDEXED, 1, 2, 3);

    // when
    store.loadKeyIndexIfNeeded(INDEXED);

    // then
    assertThat(store.keyIndex(INDEXED).state()).isEqualTo(KeyIndex.State.LOADED);
  }

  @Test
  void shouldDisableWhenCommitsGrowPastCap() throws Exception {
    // given
    reopen(config(3));
    put(INDEXED, 1, 2, 3);
    store.loadKeyIndexIfNeeded(INDEXED);

    // when
    put(INDEXED, 4);

    // then
    final var index = store.keyIndex(INDEXED);
    assertThat(index.state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(index.keys()).isEmpty();
    assertThat(index.isChanging()).isFalse();

    // and stays disabled
    store.loadKeyIndexIfNeeded(INDEXED);
    assertThat(index.state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(store.metrics().keyIndexLoads.sum()).isEqualTo(1);
  }

  @Test
  void shouldDiscardLoadWhenCommitWritesToColumnFamilyMeanwhile() {
    // given
    put(INDEXED, 1);

    // when
    access.afterNextIteratorClosed(() -> put(INDEXED, 2));
    store.loadKeyIndexIfNeeded(INDEXED);

    // then
    final var index = store.keyIndex(INDEXED);
    assertThat(index.state()).isEqualTo(KeyIndex.State.UNLOADED);
    assertThat(store.metrics().keyIndexDiscardedLoads.sum()).isEqualTo(1);

    // and the next load sees the new key
    store.loadKeyIndexIfNeeded(INDEXED);
    assertThat(indexed(INDEXED)).containsExactly(1L, 2L);
  }

  @Test
  void shouldNotDiscardLoadWhenCommitWritesToAnotherColumnFamily() {
    // given
    put(INDEXED, 1);

    // when
    access.afterNextIteratorClosed(() -> put(OTHER, 2));
    store.loadKeyIndexIfNeeded(INDEXED);

    // then
    assertThat(store.keyIndex(INDEXED).state()).isEqualTo(KeyIndex.State.LOADED);
  }

  @Test
  void shouldDisableAfterThreeDiscardedLoads() {
    // given
    put(INDEXED, 1);

    // when
    for (int i = 0; i < 3; i++) {
      final long key = 10 + i;
      access.afterNextIteratorClosed(() -> put(INDEXED, key));
      store.loadKeyIndexIfNeeded(INDEXED);
    }

    // then
    assertThat(store.keyIndex(INDEXED).state()).isEqualTo(KeyIndex.State.DISABLED);
    assertThat(store.metrics().keyIndexDiscardedLoads.sum()).isEqualTo(3);
    assertThat(store.metrics().keyIndexDisables.sum()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 3, 4, 5, 6, 7, 8})
  void shouldMatchCommittedStateAfterRandomOperations(final long seed) {
    // given
    final var random = new Random(seed);
    final var model = new TreeMap<Long, Set<Long>>();
    model.put(INDEXED, new TreeSet<>());
    model.put(OTHER, new TreeSet<>());

    // when
    for (int step = 0; step < 300; step++) {
      switch (random.nextInt(10)) {
        case 0 -> store.loadKeyIndexIfNeeded(INDEXED);
        case 1 -> store.loadKeyIndexIfNeeded(OTHER);
        case 2 -> store.awaitFlushed();
        default -> randomCommit(random, model);
      }

      // then
      for (final long columnFamily : List.of(INDEXED, OTHER)) {
        final var index = store.keyIndex(columnFamily);
        assertThat(index.isChanging()).isFalse();
        assertThat(committedKeys(columnFamily)).containsExactlyElementsOf(model.get(columnFamily));
        if (index.state() == KeyIndex.State.LOADED) {
          assertThat(indexed(columnFamily)).containsExactlyElementsOf(model.get(columnFamily));
        } else {
          assertThat(index.keys()).isEmpty();
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {11, 12, 13})
  void shouldMatchCommittedStateAfterRandomOperationsWithTinyCap(final long seed) throws Exception {
    // given
    reopen(config(8));

    // when
    shouldMatchCommittedStateAfterRandomOperations(seed);

    // then
    assertThat(store.metrics().keyIndexDisables.sum()).isPositive();
  }

  private void randomCommit(final Random random, final TreeMap<Long, Set<Long>> model) {
    final var changes = new TreeMap<RawKey, Boolean>();
    final int count = 1 + random.nextInt(4);
    for (int i = 0; i < count; i++) {
      final long columnFamily = List.of(INDEXED, OTHER, NOT_INDEXED).get(random.nextInt(3));
      changes.put(key(columnFamily, random.nextInt(12)), random.nextInt(3) > 0);
    }
    final var keys = changes.keySet().toArray(new RawKey[0]);
    final var values = new DbValue[keys.length];
    for (int i = 0; i < keys.length; i++) {
      final long columnFamily = keys[i].columnFamily();
      final long id = ByteBuffer.wrap(keys[i].bytes()).getLong(Long.BYTES);
      if (changes.get(keys[i])) {
        values[i] = value(id);
        if (model.containsKey(columnFamily)) {
          model.get(columnFamily).add(id);
        }
      } else if (model.containsKey(columnFamily)) {
        model.get(columnFamily).remove(id);
      }
    }
    store.commit(keys, values);
  }

  private void put(final long columnFamily, final long... ids) {
    final var keys = new RawKey[ids.length];
    final var values = new DbValue[ids.length];
    for (int i = 0; i < ids.length; i++) {
      keys[i] = key(columnFamily, ids[i]);
      values[i] = value(ids[i]);
    }
    store.commit(keys, values);
  }

  private void delete(final long columnFamily, final long... ids) {
    final var keys = new RawKey[ids.length];
    for (int i = 0; i < ids.length; i++) {
      keys[i] = key(columnFamily, ids[i]);
    }
    store.commit(keys, new DbValue[ids.length]);
  }

  private List<Long> indexed(final long columnFamily) {
    final var ids = new ArrayList<Long>();
    for (final var key : store.keyIndex(columnFamily).keys()) {
      ids.add(ByteBuffer.wrap(key.bytes()).getLong(Long.BYTES));
    }
    return ids;
  }

  /** The keys of a full merged scan, which is the truth that the index must match. */
  private List<Long> committedKeys(final long columnFamily) {
    final var prefix = key(columnFamily, 0).bytes().clone();
    final var ids = new ArrayList<Long>();
    try (final var source = new ViewSource(store.openView(prefix, Long.BYTES))) {
      source.seek(prefix);
      while (source.isValid() && ByteBuffer.wrap(source.key()).getLong() == columnFamily) {
        ids.add(ByteBuffer.wrap(source.key()).getLong(Long.BYTES));
        source.advance();
      }
    }
    return ids;
  }

  private static RawKey key(final long columnFamily, final long id) {
    final var buffer = ByteBuffer.allocate(2 * Long.BYTES).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return RawKey.of(buffer.putLong(columnFamily).putLong(id).array());
  }

  private static DbLong value(final long id) {
    final var value = new DbLong();
    value.wrapLong(id);
    return value;
  }
}
