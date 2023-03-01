/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DbCompositeKeyColumnFamilyTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbCompositeKey<DbString, DbLong>, DbString> columnFamily;
  private DbString firstKey;
  private DbLong secondKey;
  private DbCompositeKey<DbString, DbLong> compositeKey;
  private DbString value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    firstKey = new DbString();
    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeKey, value);
  }

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
  public void shouldUseForeachValue() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values)
        .containsExactly("baring", "different value", "world", "be good", "string", "as you know");
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values)
        .containsExactly("baring", "different value", "world", "be good", "string", "as you know");
    assertThat(firstKeyParts)
        .containsExactly("foo", "foo", "hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 34L, 37426L, 923113L, 255L);
  }

  @Test
  public void shouldUseForeachToDelete() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((key, value) -> columnFamily.deleteExisting(key));

    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).isEmpty();
    assertThat(firstKeyParts).isEmpty();
    assertThat(secondKeyParts).isEmpty();
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("world");
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "world");
    assertThat(firstKeyParts).containsExactly("foo", "foo", "hello");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 34L);
  }

  @Test
  public void shouldUseWhileTrueToDelete() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          columnFamily.deleteExisting(key);
          return key.second().getValue() != 13;
        });

    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("world", "be good", "string", "as you know");
    assertThat(firstKeyParts).containsExactly("hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(34L, 37426L, 923113L, 255L);
  }

  @Test
  public void shouldUseWhileTrueWithStartAt() {
    // given
    final var firstKey = new DbString();
    firstKey.wrapString("foo");
    final var secondKey = new DbLong();
    secondKey.wrapLong(13);
    final var startAt = new DbCompositeKey<>(firstKey, secondKey);

    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("world");
        });

    // then
    assertThat(values).containsExactly("different value", "world");
    assertThat(firstKeyParts).containsExactly("foo", "hello");
    assertThat(secondKeyParts).containsExactly(13L, 34L);
  }

  @Test
  public void shouldUseWhileEqualPrefix() {
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
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "expected value", "oh wow");
    assertThat(firstKeyParts).containsOnly("foo");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 53L, 213L);
  }

  @Test
  public void shouldUseGetWhileIterating() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foobar", 53, "expected value");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("foo", 213, "oh wow");
    upsertKeyValuePair("foo", 53, "expected value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 213, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("hello", 13, "foo");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    final List<String> values = new ArrayList<>();
    final List<String> seenStringKeys = new ArrayList<>();
    final List<Long> seenLongKeys = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          seenStringKeys.add(key.first().toString());
          seenLongKeys.add(key.second().getValue());

          key.first().wrapString("hello");
          final DbString zbString = columnFamily.get(key);
          if (zbString != null) {
            values.add(zbString.toString());
          }
        });

    // then
    assertThat(values).containsExactly("foo", "world");
    assertThat(seenStringKeys).containsOnly("foo");
    assertThat(seenLongKeys).containsExactly(12L, 13L, 53L, 213L);
  }

  @Test
  public void shouldUseWhileEqualPrefixAndTrue() {
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
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());

          return secondPart.getValue() < 50;
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "expected value");
    assertThat(firstKeyParts).containsOnly("foo");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 53L);
  }

  @Test
  public void shouldUseWhileEqualPrefixToDelete() {
    // given
    upsertKeyValuePair("foo", 12, "baring");
    upsertKeyValuePair("foo", 13, "different value");
    upsertKeyValuePair("this is the one", 255, "as you know");
    upsertKeyValuePair("hello", 34, "world");
    upsertKeyValuePair("another", 923113, "string");
    upsertKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          columnFamily.deleteExisting(key);
          return key.second().getValue() != 13;
        });

    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.first();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.second();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("world", "be good", "string", "as you know");
    assertThat(firstKeyParts).containsExactly("hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(34L, 37426L, 923113L, 255L);
  }

  private void upsertKeyValuePair(final String firstKey, final long secondKey, final String value) {
    this.firstKey.wrapString(firstKey);
    this.secondKey.wrapLong(secondKey);

    this.value.wrapString(value);
    columnFamily.upsert(compositeKey, this.value);
  }
}
