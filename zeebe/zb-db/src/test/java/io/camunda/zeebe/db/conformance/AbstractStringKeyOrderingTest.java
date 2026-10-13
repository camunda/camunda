/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins how string keys are ordered. {@link DbString} serializes as a 4-byte big-endian length
 * followed by the bytes, so keys sort by length first and by content second - not
 * lexicographically.
 */
public abstract class AbstractStringKeyOrderingTest extends ConformanceDbTestBase {

  @Test
  void shouldOrderStringKeysByLengthBeforeContent() {
    // given
    final ColumnFamily<DbString, DbLong> cf = columnFamily(Cf.ONE, new DbString(), new DbLong());
    for (final String key : new String[] {"aaa", "b", "zz", "a", "ab", "aab"}) {
      cf.insert(dbString(key), dbLong(1));
    }

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.toString()));

    // then
    assertThat(visited).containsExactly("a", "b", "ab", "zz", "aaa", "aab");
  }

  @Test
  void shouldSortLongerStringAfterShorterStringWithLargerContent() {
    // given
    final ColumnFamily<DbString, DbLong> cf = columnFamily(Cf.ONE, new DbString(), new DbLong());
    cf.insert(dbString("aaa"), dbLong(1));
    cf.insert(dbString("zz"), dbLong(2));

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.toString()));

    // then
    assertThat(visited).containsExactly("zz", "aaa");
  }

  @Test
  void shouldSortEmptyStringAsMinimum() {
    // given
    final ColumnFamily<DbString, DbLong> cf = columnFamily(Cf.ONE, new DbString(), new DbLong());
    cf.insert(dbString("a"), dbLong(1));
    cf.insert(dbString(""), dbLong(2));
    cf.insert(dbString("zzzz"), dbLong(3));

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.toString()));

    // then
    assertThat(visited).containsExactly("", "a", "zzzz");
    assertThat(cf.exists(dbString(""))).isTrue();
  }

  @Test
  void shouldKeepEmptyStringFirstWhenScanningFromItAsStartKey() {
    // given
    final ColumnFamily<DbString, DbLong> cf = columnFamily(Cf.ONE, new DbString(), new DbLong());
    cf.insert(dbString("b"), dbLong(1));
    cf.insert(dbString(""), dbLong(2));

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileTrue(
        dbString(""),
        (key, value) -> {
          visited.add(key.toString());
          return true;
        });

    // then
    assertThat(visited).containsExactly("", "b");
  }

  @Test
  void shouldOrderCompositeKeyByStringLengthThenContentThenSecondComponent() {
    // given
    final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbLong> cf =
        columnFamily(Cf.ONE, new DbCompositeKey<>(new DbString(), new DbLong()), new DbLong());
    cf.insert(stringLong("aaa", 1), dbLong(1));
    cf.insert(stringLong("zz", 9), dbLong(1));
    cf.insert(stringLong("zz", 2), dbLong(1));
    cf.insert(stringLong("", 7), dbLong(1));

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(key.first().toString() + "/" + key.second().getValue()));

    // then
    assertThat(visited).containsExactly("/7", "zz/2", "zz/9", "aaa/1");
  }

  @Test
  void shouldOrderTenantAwareKeysWithPrefixPlacementByTenantFirst() {
    // given
    final var cf = tenantAwareColumnFamily(PlacementType.PREFIX);
    insertTenantAware(cf, PlacementType.PREFIX, "aaa", 1);
    insertTenantAware(cf, PlacementType.PREFIX, "zz", 5);
    insertTenantAware(cf, PlacementType.PREFIX, "zz", 2);
    insertTenantAware(cf, PlacementType.PREFIX, "", 9);

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(describe(key)));

    // then - tenant "zz" (length 2) sorts before tenant "aaa" (length 3)
    assertThat(visited).containsExactly("/9", "zz/2", "zz/5", "aaa/1");
  }

  @Test
  void shouldOrderTenantAwareKeysWithSuffixPlacementByWrappedKeyFirst() {
    // given
    final var cf = tenantAwareColumnFamily(PlacementType.SUFFIX);
    insertTenantAware(cf, PlacementType.SUFFIX, "aaa", 1);
    insertTenantAware(cf, PlacementType.SUFFIX, "zz", 5);
    insertTenantAware(cf, PlacementType.SUFFIX, "zz", 2);
    insertTenantAware(cf, PlacementType.SUFFIX, "", 9);

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(describe(key)));

    // then
    assertThat(visited).containsExactly("aaa/1", "zz/2", "zz/5", "/9");
  }

  @Test
  void shouldOrderTenantsByLengthWhenSuffixPlacedWrappedKeysAreEqual() {
    // given
    final var cf = tenantAwareColumnFamily(PlacementType.SUFFIX);
    insertTenantAware(cf, PlacementType.SUFFIX, "aaa", 7);
    insertTenantAware(cf, PlacementType.SUFFIX, "zz", 7);
    insertTenantAware(cf, PlacementType.SUFFIX, "", 7);

    // when
    final List<String> visited = new ArrayList<>();
    cf.forEach((key, value) -> visited.add(describe(key)));

    // then
    assertThat(visited).containsExactly("/7", "zz/7", "aaa/7");
  }

  @Test
  void shouldScanOnlyOneTenantWithPrefixPlacement() {
    // given
    final var cf = tenantAwareColumnFamily(PlacementType.PREFIX);
    insertTenantAware(cf, PlacementType.PREFIX, "tenant-a", 3);
    insertTenantAware(cf, PlacementType.PREFIX, "tenant-a", 1);
    insertTenantAware(cf, PlacementType.PREFIX, "tenant-b", 2);

    // when
    final List<String> visited = new ArrayList<>();
    cf.whileEqualPrefix(
        dbString("tenant-a"),
        (key, value) -> {
          visited.add(describe(key));
          return true;
        });

    // then
    assertThat(visited).containsExactly("tenant-a/1", "tenant-a/3");
  }

  private ColumnFamily<DbTenantAwareKey<DbLong>, DbLong> tenantAwareColumnFamily(
      final PlacementType placement) {
    return columnFamily(
        Cf.ONE, new DbTenantAwareKey<>(new DbString(), new DbLong(), placement), new DbLong());
  }

  private static void insertTenantAware(
      final ColumnFamily<DbTenantAwareKey<DbLong>, DbLong> cf,
      final PlacementType placement,
      final String tenant,
      final long key) {
    cf.insert(new DbTenantAwareKey<>(dbString(tenant), dbLong(key), placement), dbLong(key));
  }

  private static String describe(final DbTenantAwareKey<DbLong> key) {
    return key.tenantKey().toString() + "/" + key.wrappedKey().getValue();
  }
}
