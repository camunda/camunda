/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.LayeredStoreMetrics;
import io.camunda.zeebe.db.layered.segment.ChunkPool;
import io.camunda.zeebe.db.layered.segment.ChunkWriter;
import io.camunda.zeebe.db.layered.util.CountingBytesStore;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LayeredColumnFamilyTest {

  private static final String STORE_NAME = "typed-cf";

  private final InMemoryDurableState durable = new InMemoryDurableState();
  private LayeredKeyValueStore store;
  private LayeredColumnFamily<DbLong, DbString> columnFamily;
  private final DbLong key = new DbLong();
  private final DbString value = new DbString();

  @BeforeEach
  void setUp() {
    store = new LayeredKeyValueStore(STORE_NAME, durable.store(STORE_NAME), 1_000_000, false, 4);
    columnFamily = new LayeredColumnFamily<>(store, key, value);
  }

  private void put(final long keyValue, final String valueString) {
    key.wrapLong(keyValue);
    value.wrapString(valueString);
    columnFamily.upsert(key, value);
  }

  private String get(final long keyValue) {
    key.wrapLong(keyValue);
    final DbString result = columnFamily.get(key);
    return result == null ? null : result.toString();
  }

  @Test
  void shouldPutAndGetValue() {
    // given
    put(23L, "foo");

    // when
    final String actual = get(23L);

    // then
    assertThat(actual).isEqualTo("foo");
  }

  @Test
  void shouldReturnNullIfKeyAbsent() {
    // given
    put(23L, "foo");

    // when
    final String actual = get(42L);

    // then
    assertThat(actual).isNull();
  }

  @Test
  void shouldGetIndependentValueWithSupplier() {
    // given
    put(23L, "foo");
    put(42L, "bar");
    key.wrapLong(23L);

    // when
    final DbString supplied = columnFamily.get(key, DbString::new);
    // a subsequent read into the shared instance must not affect the supplied one
    get(42L);

    // then
    assertThat(supplied).isNotSameAs(value);
    assertThat(supplied.toString()).isEqualTo("foo");
  }

  @Test
  void shouldReturnNullWithSupplierIfKeyAbsent() {
    // given
    key.wrapLong(23L);

    // when
    final DbString actual = columnFamily.get(key, DbString::new);

    // then
    assertThat(actual).isNull();
  }

  @Test
  void shouldInsertNewKey() {
    // given
    key.wrapLong(23L);
    value.wrapString("foo");

    // when
    columnFamily.insert(key, value);

    // then
    assertThat(get(23L)).isEqualTo("foo");
  }

  @Test
  void shouldProbeDelegateExactlyOnceOnInsertOfFreshKey() {
    // given -- a probe-counting delegate under a store without the absence watermark
    final CountingBytesStore counting = new CountingBytesStore(durable.store(STORE_NAME));
    final LayeredKeyValueStore countingStore =
        new LayeredKeyValueStore(STORE_NAME, counting, 1_000_000, false, 4);
    final LayeredColumnFamily<DbLong, DbString> countingColumnFamily =
        new LayeredColumnFamily<>(countingStore, key, value);
    key.wrapLong(7L);
    value.wrapString("fresh");

    // when -- the insert's exists check pays the only probe and caches the negative, which then
    // answers the write-time flushed check without a second probe
    countingColumnFamily.insert(key, value);

    // then
    assertThat(counting.gets()).isEqualTo(1);
  }

  @Test
  void shouldNotProbeDelegateOnInsertOfFreshKeyUnderAbsenceWatermark() {
    // given -- an empty-at-open store: every key is provably absent until something drains
    final CountingBytesStore counting = new CountingBytesStore(durable.store(STORE_NAME));
    final LayeredKeyValueStore countingStore =
        new LayeredKeyValueStore(
            STORE_NAME,
            counting,
            1_000_000,
            false,
            4,
            LayeredStoreMetrics.noop(),
            new ChunkWriter(new ChunkPool()),
            true);
    final LayeredColumnFamily<DbLong, DbString> countingColumnFamily =
        new LayeredColumnFamily<>(countingStore, key, value);
    key.wrapLong(7L);
    value.wrapString("fresh");

    // when -- neither the exists check nor the write's flushed check touches the delegate
    countingColumnFamily.insert(key, value);

    // then -- and the consistency check still trips on a duplicate, probe-free
    assertThat(counting.gets()).isZero();
    assertThatThrownBy(() -> countingColumnFamily.insert(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class);
    assertThat(counting.gets()).isZero();
  }

  @Test
  void shouldThrowOnInsertExistingKey() {
    // given
    put(23L, "foo");
    key.wrapLong(23L);
    value.wrapString("bar");

    // when / then
    assertThatThrownBy(() -> columnFamily.insert(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key DbLong{23} in ColumnFamily typed-cf already exists");
  }

  @Test
  void shouldUpdateExistingKey() {
    // given
    put(23L, "foo");
    key.wrapLong(23L);
    value.wrapString("bar");

    // when
    columnFamily.update(key, value);

    // then
    assertThat(get(23L)).isEqualTo("bar");
  }

  @Test
  void shouldThrowOnUpdateMissingKey() {
    // given
    key.wrapLong(23L);
    value.wrapString("bar");

    // when / then
    assertThatThrownBy(() -> columnFamily.update(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key DbLong{23} in ColumnFamily typed-cf does not exist");
  }

  @Test
  void shouldUpsertMissingAndExistingKey() {
    // given
    put(23L, "foo");

    // when
    put(23L, "bar");
    put(42L, "baz");

    // then
    assertThat(get(23L)).isEqualTo("bar");
    assertThat(get(42L)).isEqualTo("baz");
  }

  @Test
  void shouldDeleteExistingKey() {
    // given
    put(23L, "foo");
    key.wrapLong(23L);

    // when
    columnFamily.deleteExisting(key);

    // then
    assertThat(get(23L)).isNull();
  }

  @Test
  void shouldThrowOnDeleteExistingWithMissingKey() {
    // given
    key.wrapLong(23L);

    // when / then
    assertThatThrownBy(() -> columnFamily.deleteExisting(key))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key DbLong{23} in ColumnFamily typed-cf does not exist");
  }

  @Test
  void shouldIgnoreDeleteIfExistsOnMissingKey() {
    // given
    put(23L, "foo");
    key.wrapLong(42L);

    // when
    columnFamily.deleteIfExists(key);

    // then
    assertThat(get(23L)).isEqualTo("foo");
  }

  @Test
  void shouldDeleteIfExistsOnExistingKey() {
    // given
    put(23L, "foo");
    key.wrapLong(23L);

    // when
    columnFamily.deleteIfExists(key);

    // then
    assertThat(get(23L)).isNull();
  }

  @Test
  void shouldCheckExistence() {
    // given
    put(23L, "foo");

    // when
    key.wrapLong(23L);
    final boolean exists = columnFamily.exists(key);
    key.wrapLong(42L);
    final boolean missing = columnFamily.exists(key);

    // then
    assertThat(exists).isTrue();
    assertThat(missing).isFalse();
  }

  @Test
  void shouldCheckIsEmpty() {
    // given / when / then
    assertThat(columnFamily.isEmpty()).isTrue();

    put(23L, "foo");
    assertThat(columnFamily.isEmpty()).isFalse();

    key.wrapLong(23L);
    columnFamily.deleteExisting(key);
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  @Test
  void shouldCountEntries() {
    // given
    put(23L, "foo");
    put(42L, "bar");
    put(1L, "baz");

    // when / then
    assertThat(columnFamily.count()).isEqualTo(3);
  }

  @Test
  void shouldVisitValuesInKeyOrder() {
    // given -- numeric order equals unsigned byte order for non-negative DbLong keys
    put(37L, "c");
    put(1L, "a");
    put(12L, "b");

    // when
    final List<String> visited = new ArrayList<>();
    columnFamily.forEach(visitedValue -> visited.add(visitedValue.toString()));

    // then
    assertThat(visited).containsExactly("a", "b", "c");
  }

  @Test
  void shouldVisitPairsInKeyOrder() {
    // given
    put(37L, "c");
    put(1L, "a");
    put(12L, "b");

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    final List<String> visitedValues = new ArrayList<>();
    columnFamily.forEach(
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          visitedValues.add(visitedValue.toString());
        });

    // then
    assertThat(visitedKeys).containsExactly(1L, 12L, 37L);
    assertThat(visitedValues).containsExactly("a", "b", "c");
  }

  @Test
  void shouldVisitKeysOnlyInKeyOrder() {
    // given
    put(37L, "c");
    put(1L, "a");
    put(12L, "b");

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.forEachKey(visitedKey -> visitedKeys.add(visitedKey.getValue()));

    // then
    assertThat(visitedKeys).containsExactly(1L, 12L, 37L);
  }

  @Test
  void shouldStopWhileTrueEarly() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrue(
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return visitedKey.getValue() < 2L;
        });

    // then
    assertThat(visitedKeys).containsExactly(1L, 2L);
  }

  @Test
  void shouldStopKeyOnlyWhileTrueEarly() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrue(
        visitedKey -> {
          visitedKeys.add(visitedKey.getValue());
          return visitedKey.getValue() < 2L;
        });

    // then
    assertThat(visitedKeys).containsExactly(1L, 2L);
  }

  @Test
  void shouldStartWhileTrueAtExistingKey() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(2L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return true;
        });

    // then
    assertThat(visitedKeys).containsExactly(2L, 3L);
  }

  @Test
  void shouldStartWhileTrueAfterMissingKey() {
    // given
    put(1L, "a");
    put(3L, "c");
    put(4L, "d");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(2L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return true;
        });

    // then
    assertThat(visitedKeys).containsExactly(3L, 4L);
  }

  @Test
  void shouldIterateReverseFromStartKey() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(2L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrueReverse(
        startAt,
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return true;
        });

    // then
    assertThat(visitedKeys).containsExactly(2L, 1L);
  }

  @Test
  void shouldIterateReverseFromMissingStartKeyJustBefore() {
    // given
    put(1L, "a");
    put(3L, "c");
    put(4L, "d");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(2L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrueReverse(
        startAt,
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return true;
        });

    // then
    assertThat(visitedKeys).containsExactly(1L);
  }

  @Test
  void shouldStopReverseIterationEarly() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(3L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrueReverse(
        startAt,
        (visitedKey, visitedValue) -> {
          visitedKeys.add(visitedKey.getValue());
          return false;
        });

    // then
    assertThat(visitedKeys).containsExactly(3L);
  }

  @Test
  void shouldIterateKeysReverse() {
    // given
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");
    final DbLong startAt = new DbLong();
    startAt.wrapLong(3L);

    // when
    final List<Long> visitedKeys = new ArrayList<>();
    columnFamily.whileTrueReverse(
        startAt,
        visitedKey -> {
          visitedKeys.add(visitedKey.getValue());
          return true;
        });

    // then
    assertThat(visitedKeys).containsExactly(3L, 2L, 1L);
  }

  @Test
  void shouldAliasFlyweightsDuringIteration() {
    // given -- the documented gotcha: visited flyweights alias the adapter's single instances
    put(1L, "a");
    put(2L, "b");
    put(3L, "c");

    // when
    final List<DbLong> capturedKeys = new ArrayList<>();
    final List<DbString> capturedValues = new ArrayList<>();
    columnFamily.forEach(
        (visitedKey, visitedValue) -> {
          capturedKeys.add(visitedKey);
          capturedValues.add(visitedValue);
        });

    // then -- every captured reference is the same instance, holding the last visited entry
    assertThat(capturedKeys).allSatisfy(captured -> assertThat(captured).isSameAs(key));
    assertThat(capturedValues).allSatisfy(captured -> assertThat(captured).isSameAs(value));
    assertThat(capturedKeys.getFirst().getValue()).isEqualTo(3L);
    assertThat(capturedValues.getFirst().toString()).isEqualTo("c");
  }

  @Test
  void shouldReadAcrossLayersAfterPromoteAndFreeze() {
    // given -- entries spread over pipeline (frozen), active (promoted) and delegate
    durable
        .store(STORE_NAME)
        .put(serializedKey(1L), TypedBytes.serialize(dbString("delegate-only")));
    put(2L, "frozen");
    store.promote();
    store.freeze(1L);
    put(3L, "promoted");
    store.promote();
    put(4L, "staged");

    // when
    final List<String> visited = new ArrayList<>();
    columnFamily.forEach(visitedValue -> visited.add(visitedValue.toString()));

    // then
    assertThat(visited).containsExactly("delegate-only", "frozen", "promoted", "staged");
    assertThat(get(1L)).isEqualTo("delegate-only");
    assertThat(get(2L)).isEqualTo("frozen");
  }

  // ---- composite-key prefix scans ----

  private LayeredColumnFamily<DbCompositeKey<DbLong, DbLong>, DbString> compositeColumnFamily() {
    return new LayeredColumnFamily<>(
        store, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbString());
  }

  private void putComposite(
      final LayeredColumnFamily<DbCompositeKey<DbLong, DbLong>, DbString> compositeFamily,
      final long first,
      final long second,
      final String valueString) {
    final DbCompositeKey<DbLong, DbLong> compositeKey =
        new DbCompositeKey<>(new DbLong(), new DbLong());
    compositeKey.first().wrapLong(first);
    compositeKey.second().wrapLong(second);
    final DbString compositeValue = new DbString();
    compositeValue.wrapString(valueString);
    compositeFamily.upsert(compositeKey, compositeValue);
  }

  @Test
  void shouldVisitOnlyEntriesWithEqualPrefix() {
    // given -- neighbors on both sides of the prefix must be excluded
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 0L, 9L, "below");
    putComposite(compositeFamily, 1L, 2L, "in-2");
    putComposite(compositeFamily, 1L, 1L, "in-1");
    putComposite(compositeFamily, 2L, 0L, "above");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);

    // when
    final List<Long> visitedSeconds = new ArrayList<>();
    final List<String> visitedValues = new ArrayList<>();
    compositeFamily.whileEqualPrefix(
        prefix,
        (visitedKey, visitedValue) -> {
          visitedSeconds.add(visitedKey.second().getValue());
          visitedValues.add(visitedValue.toString());
        });

    // then
    assertThat(visitedSeconds).containsExactly(1L, 2L);
    assertThat(visitedValues).containsExactly("in-1", "in-2");
  }

  @Test
  void shouldStopWhileEqualPrefixEarly() {
    // given
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 1L, 1L, "in-1");
    putComposite(compositeFamily, 1L, 2L, "in-2");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);

    // when
    final List<Long> visitedSeconds = new ArrayList<>();
    compositeFamily.whileEqualPrefix(
        prefix,
        (visitedKey, visitedValue) -> {
          visitedSeconds.add(visitedKey.second().getValue());
          return false;
        });

    // then
    assertThat(visitedSeconds).containsExactly(1L);
  }

  @Test
  void shouldStartWhileEqualPrefixAtStartKey() {
    // given
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 1L, 1L, "in-1");
    putComposite(compositeFamily, 1L, 2L, "in-2");
    putComposite(compositeFamily, 1L, 3L, "in-3");
    putComposite(compositeFamily, 2L, 0L, "above");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);
    final DbCompositeKey<DbLong, DbLong> startAt = new DbCompositeKey<>(new DbLong(), new DbLong());
    startAt.first().wrapLong(1L);
    startAt.second().wrapLong(2L);

    // when
    final List<Long> visitedSeconds = new ArrayList<>();
    compositeFamily.whileEqualPrefix(
        prefix,
        startAt,
        (visitedKey, visitedValue) -> {
          visitedSeconds.add(visitedKey.second().getValue());
          return true;
        });

    // then
    assertThat(visitedSeconds).containsExactly(2L, 3L);
  }

  @Test
  void shouldVisitKeysOnlyWithEqualPrefix() {
    // given
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 0L, 9L, "below");
    putComposite(compositeFamily, 1L, 1L, "in-1");
    putComposite(compositeFamily, 1L, 2L, "in-2");
    putComposite(compositeFamily, 2L, 0L, "above");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);

    // when
    final List<Long> visitedSeconds = new ArrayList<>();
    compositeFamily.whileEqualPrefix(
        prefix,
        visitedKey -> {
          visitedSeconds.add(visitedKey.second().getValue());
        });

    // then
    assertThat(visitedSeconds).containsExactly(1L, 2L);
  }

  @Test
  void shouldDeleteVisitedKeysDuringWhileEqualPrefix() {
    // given -- prefixed entries spread over a pipeline segment, the active overlay and staging
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 1L, 1L, "segment");
    store.promote();
    store.freeze(1L);
    putComposite(compositeFamily, 1L, 2L, "active");
    store.promote();
    putComposite(compositeFamily, 1L, 3L, "staging");
    putComposite(compositeFamily, 2L, 0L, "outside");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);

    // when -- the engine pattern: the visitor deletes the key it is visiting
    final List<Long> visitedSeconds = new ArrayList<>();
    compositeFamily.whileEqualPrefix(
        prefix,
        (visitedKey, visitedValue) -> {
          visitedSeconds.add(visitedKey.second().getValue());
          compositeFamily.deleteExisting(visitedKey);
        });

    // then -- every prefixed key was visited and deleted; the outsider survives
    assertThat(visitedSeconds).containsExactly(1L, 2L, 3L);
    assertThat(compositeFamily.countEqualPrefix(prefix)).isZero();
    assertThat(compositeFamily.count()).isEqualTo(1);
  }

  @Test
  void shouldCountEqualPrefix() {
    // given
    final var compositeFamily = compositeColumnFamily();
    putComposite(compositeFamily, 0L, 9L, "below");
    putComposite(compositeFamily, 1L, 1L, "in-1");
    putComposite(compositeFamily, 1L, 2L, "in-2");
    putComposite(compositeFamily, 2L, 0L, "above");
    final DbLong prefix = new DbLong();
    prefix.wrapLong(1L);

    // when / then
    assertThat(compositeFamily.countEqualPrefix(prefix)).isEqualTo(2);
    assertThat(compositeFamily.count()).isEqualTo(4);
  }

  private static byte[] serializedKey(final long keyValue) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(keyValue);
    return TypedBytes.serialize(dbKey);
  }

  private static DbString dbString(final String string) {
    final DbString dbString = new DbString();
    dbString.wrapString(string);
    return dbString;
  }
}
