/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.typed;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class LayeredViewReaderTest {

  private static final String STORE_NAME = "view-cf";

  private final InMemoryDurableState durable = new InMemoryDurableState();
  private LayeredKeyValueStore store;
  private LayeredColumnFamily<DbLong, DbString> columnFamily;

  @BeforeEach
  void setUp() {
    store = new LayeredKeyValueStore(STORE_NAME, durable.store(STORE_NAME), 1_000_000, false, 4);
    columnFamily = new LayeredColumnFamily<>(store, new DbLong(), new DbString());
  }

  private void put(final long keyValue, final String valueString) {
    final DbLong key = new DbLong();
    key.wrapLong(keyValue);
    final DbString value = new DbString();
    value.wrapString(valueString);
    columnFamily.upsert(key, value);
  }

  private void promoteAndFreeze(final long watermark) {
    store.promote();
    store.freeze(watermark);
  }

  /** A frozen-cut view over the store's current segments plus a snapshot of the durable state. */
  private ReadOnlyView buildView() {
    return new ReadOnlyView(
        Map.of(STORE_NAME, store.segmentsNewestFirst()), durable.snapshotSource().takeSnapshot());
  }

  private LayeredViewReader<DbLong, DbString> reader(final ReadOnlyView view) {
    return new LayeredViewReader<>(view, STORE_NAME, new DbLong(), new DbString());
  }

  private static DbLong dbLong(final long value) {
    final DbLong dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }

  @Test
  void shouldReadValueFromFrozenCut() {
    // given
    put(23L, "foo");
    promoteAndFreeze(1L);

    // when
    final LayeredViewReader<DbLong, DbString> viewReader = reader(buildView());
    final DbString actual = viewReader.get(dbLong(23L));

    // then
    assertThat(actual).isNotNull();
    assertThat(actual.toString()).isEqualTo("foo");
    assertThat(viewReader.exists(dbLong(23L))).isTrue();
    assertThat(viewReader.exists(dbLong(42L))).isFalse();
  }

  @Test
  void shouldReturnNullForAbsentKey() {
    // given
    promoteAndFreeze(1L);

    // when
    final LayeredViewReader<DbLong, DbString> viewReader = reader(buildView());

    // then
    assertThat(viewReader.get(dbLong(23L))).isNull();
  }

  @Test
  void shouldStillSeeValueDeletedAfterTheFreeze() {
    // given -- staleness is the contract: the view reflects the cut, not the live store
    put(23L, "foo");
    promoteAndFreeze(1L);
    final LayeredViewReader<DbLong, DbString> viewReader = reader(buildView());

    // when -- the owner deletes and promotes after the view was built
    final DbLong key = dbLong(23L);
    columnFamily.deleteExisting(key);
    store.promote();

    // then -- the owner no longer sees the key, the view still does
    assertThat(columnFamily.get(key)).isNull();
    assertThat(viewReader.get(dbLong(23L)).toString()).isEqualTo("foo");
  }

  @Test
  void shouldNotSeeValuePutAfterTheFreeze() {
    // given
    put(23L, "foo");
    promoteAndFreeze(1L);
    final LayeredViewReader<DbLong, DbString> viewReader = reader(buildView());

    // when
    put(42L, "bar");
    store.promote();

    // then
    assertThat(columnFamily.get(dbLong(42L))).isNotNull();
    assertThat(viewReader.get(dbLong(42L))).isNull();
  }

  @Test
  void shouldHideSnapshotValueBehindFrozenTombstone() {
    // given -- the delegate holds the key, but the frozen cut contains its deletion
    final DbString seeded = new DbString();
    seeded.wrapString("committed");
    durable.store(STORE_NAME).put(TypedBytes.serialize(dbLong(23L)), TypedBytes.serialize(seeded));
    columnFamily.deleteExisting(dbLong(23L));
    promoteAndFreeze(1L);

    // when
    final LayeredViewReader<DbLong, DbString> viewReader = reader(buildView());

    // then
    assertThat(viewReader.get(dbLong(23L))).isNull();
    assertThat(viewReader.exists(dbLong(23L))).isFalse();
  }

  @Test
  void shouldScanPrefixMergingSegmentsAndSnapshot() {
    // given -- a composite-key store with entries split between the delegate and a frozen segment
    final var compositeFamily =
        new LayeredColumnFamily<>(
            store, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbString());
    putDurableComposite(1L, 1L, "snapshot-1");
    putDurableComposite(1L, 3L, "shadowed");
    putDurableComposite(2L, 1L, "above");
    putComposite(compositeFamily, 1L, 2L, "segment-2");
    putComposite(compositeFamily, 1L, 3L, "segment-3");
    putComposite(compositeFamily, 0L, 9L, "below");
    promoteAndFreeze(1L);
    final var viewReader =
        new LayeredViewReader<>(
            buildView(),
            STORE_NAME,
            new DbCompositeKey<>(new DbLong(), new DbLong()),
            new DbString());
    final DbLong prefix = dbLong(1L);

    // when
    final List<Long> visitedSeconds = new ArrayList<>();
    final List<String> visitedValues = new ArrayList<>();
    viewReader.whileEqualPrefix(
        prefix,
        (visitedKey, visitedValue) -> {
          visitedSeconds.add(visitedKey.second().getValue());
          visitedValues.add(visitedValue.toString());
        });

    // then -- merged in key order, the segment version shadowing the snapshot one on equal keys
    assertThat(visitedSeconds).containsExactly(1L, 2L, 3L);
    assertThat(visitedValues).containsExactly("snapshot-1", "segment-2", "segment-3");
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
    final DbString value = new DbString();
    value.wrapString(valueString);
    compositeFamily.upsert(compositeKey, value);
  }

  private void putDurableComposite(final long first, final long second, final String valueString) {
    final DbCompositeKey<DbLong, DbLong> compositeKey =
        new DbCompositeKey<>(new DbLong(), new DbLong());
    compositeKey.first().wrapLong(first);
    compositeKey.second().wrapLong(second);
    final DbString value = new DbString();
    value.wrapString(valueString);
    durable.store(STORE_NAME).put(TypedBytes.serialize(compositeKey), TypedBytes.serialize(value));
  }
}
