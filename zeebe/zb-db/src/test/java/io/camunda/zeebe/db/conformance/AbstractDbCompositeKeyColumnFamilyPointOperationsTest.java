/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.DbString;
import org.junit.Test;

/**
 * Point-operation (insert, update, get, delete, count) conformance tests for a column family keyed
 * by a composite key. A backend without iteration support can run this class on its own.
 */
public abstract class AbstractDbCompositeKeyColumnFamilyPointOperationsTest
    extends AbstractDbCompositeKeyColumnFamilyTestBase {

  @Test
  public void shouldUpsertValue() {
    // given
    firstKey.wrapString("foo");
    secondKey.wrapLong(2);
    value.wrapString("baring");

    // when
    columnFamily.upsert(compositeKey, value);
    value.wrapString("yes");

    // then
    final DbString zbLong = columnFamily.get(compositeKey);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.toString()).isEqualTo("baring");

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }

  @Test
  public void shouldCount() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foobar", 53, "expected value");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("foo", 213, "oh wow");
    upsertKeyValuePair("foo", 53, "expected value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final long count = columnFamily.count();

    // then
    assertThat(count).describedAs("Counts all entries").isEqualTo(9);
  }

  @Test
  public void shouldCountEqualPrefix() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foobar", 53, "expected value");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("foo", 213, "oh wow");
    upsertKeyValuePair("foo", 53, "expected value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    final long count = columnFamily.countEqualPrefix(firstKey);

    // then
    assertThat(count).describedAs("Only counts entries matching 'foo'").isEqualTo(4);
  }

  @Test
  public void shouldCountWhileEqualPrefixToDelete() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("foo", 14, "different value");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    firstKey.wrapString("foo");
    secondKey.wrapLong(14L);
    columnFamily.deleteExisting(compositeKey);

    // when
    firstKey.wrapString("foo");
    final long count = columnFamily.countEqualPrefix(firstKey);

    // then
    assertThat(count).describedAs("Only counts entries matching 'foo'").isEqualTo(2);
  }
}
