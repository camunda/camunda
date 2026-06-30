/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.state.api.KeyValueStore;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The behaviour every {@link KeyValueStore} implementation must satisfy. Subclasses supply a store
 * backed by RocksDB or by the heap, so both honour the same read/write/scan contract.
 */
abstract class AbstractKeyValueStoreContractTest {

  /** A fresh, empty {@code DbLong -> DbString} store. */
  protected abstract KeyValueStore<DbLong, DbString> longStore();

  /** A fresh, empty {@code (DbLong, DbString) -> DbString} store. */
  protected abstract KeyValueStore<DbCompositeKey<DbLong, DbString>, DbString> compositeStore();

  @Test
  void shouldPutAndGet() {
    // given
    final KeyValueStore<DbLong, DbString> store = longStore();

    // when
    store.put(longKey(1L), string("one"));

    // then
    assertThat(store.get(longKey(1L))).hasValueSatisfying(v -> assertThat(v).hasToString("one"));
  }

  @Test
  void shouldReturnEmptyWhenAbsent() {
    assertThat(longStore().get(longKey(404L))).isEmpty();
  }

  @Test
  void shouldOverwriteOnPut() {
    // given
    final KeyValueStore<DbLong, DbString> store = longStore();
    store.put(longKey(1L), string("first"));

    // when
    store.put(longKey(1L), string("second"));

    // then
    assertThat(store.get(longKey(1L))).hasValueSatisfying(v -> assertThat(v).hasToString("second"));
  }

  @Test
  void shouldDelete() {
    // given
    final KeyValueStore<DbLong, DbString> store = longStore();
    store.put(longKey(1L), string("one"));

    // when
    store.delete(longKey(1L));

    // then
    assertThat(store.exists(longKey(1L))).isFalse();
    assertThat(store.get(longKey(1L))).isEmpty();
  }

  @Test
  void shouldReportExists() {
    // given
    final KeyValueStore<DbLong, DbString> store = longStore();
    store.put(longKey(7L), string("seven"));

    // then
    assertThat(store.exists(longKey(7L))).isTrue();
    assertThat(store.exists(longKey(8L))).isFalse();
  }

  @Test
  void shouldVisitAllEntriesInKeyOrder() {
    // given — inserted out of order
    final KeyValueStore<DbLong, DbString> store = longStore();
    store.put(longKey(3L), string("c"));
    store.put(longKey(1L), string("a"));
    store.put(longKey(2L), string("b"));

    // when
    final List<Long> keys = new ArrayList<>();
    store.forEach((k, v) -> keys.add(k.getValue()));

    // then — ascending key order
    assertThat(keys).containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldPrefixScanByFirstComponent() {
    // given — two instances, each with several variables
    final KeyValueStore<DbCompositeKey<DbLong, DbString>, DbString> store = compositeStore();
    store.put(compositeKey(1L, "region"), string("EU"));
    store.put(compositeKey(1L, "tier"), string("gold"));
    store.put(compositeKey(2L, "region"), string("US"));

    // when — scan everything under instance 1
    final List<String> values = new ArrayList<>();
    store.prefixScan(longKey(1L), (k, v) -> values.add(k.second() + "=" + v));

    // then — exactly instance 1's entries, instance 2 excluded (within-prefix order is the
    // key encoding's concern, not this contract)
    assertThat(values).containsExactlyInAnyOrder("region=EU", "tier=gold");
  }

  private static DbLong longKey(final long value) {
    final DbLong key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  private static DbString string(final String value) {
    final DbString s = new DbString();
    s.wrapString(value);
    return s;
  }

  private static DbCompositeKey<DbLong, DbString> compositeKey(
      final long first, final String second) {
    final DbCompositeKey<DbLong, DbString> key = new DbCompositeKey<>(new DbLong(), new DbString());
    key.first().wrapLong(first);
    key.second().wrapString(second);
    return key;
  }
}
