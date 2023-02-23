/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

public final class DbStringColumnFamilyTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbString, DbString> columnFamily;
  private DbString key;
  private DbString value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    key = new DbString();
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  public void shouldUpsertValue() {
    // given
    key.wrapString("foo");
    value.wrapString("baring");

    // when
    columnFamily.upsert(key, value);
    value.wrapString("yes");

    // then
    final DbString zbLong = columnFamily.get(key);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.toString()).isEqualTo("baring");

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }

  @Test
  public void shouldUseForeachValue() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("this is the one", "as you know");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("might", "be good");

    // when
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string", "as you know");
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("this is the one", "as you know");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("might", "be good");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string", "as you know");
    assertThat(keys).containsExactly("foo", "hello", "might", "another", "this is the one");
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("might", "be good");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("this is the one", "as you know");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("string");
        });

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string");
    assertThat(keys).containsExactly("foo", "hello", "might", "another");
  }

  @Test
  public void shouldUseWhileTrueWithStartAtKey() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("might", "be good");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("this is the one", "as you know");
    final var startAtKey = new DbString();
    startAtKey.wrapString("hello");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAtKey,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("string");
        });

    // then
    assertThat(values).containsExactly("world", "be good", "string");
    assertThat(keys).containsExactly("hello", "might", "another");
  }

  @Test
  public void shouldUseWhileEqualPrefix() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("this is the one", "as you know");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("and", "be good");

    // when
    key.wrapString("an");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !key.toString().equalsIgnoreCase("world");
        });

    // then should be empty
    // since whileEqual should work only for composite keys
    // DbString writes length of the string before the content
    // -> that is why prefix search will not work
    assertThat(values).isEmpty();
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUseWhileEqualPrefixLikeGet() {
    // given
    upsertKeyValuePair("foo", "baring");
    upsertKeyValuePair("this is the one", "as you know");
    upsertKeyValuePair("hello", "world");
    upsertKeyValuePair("another", "string");
    upsertKeyValuePair("and", "be good");

    // when
    key.wrapString("and");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !key.toString().equalsIgnoreCase("world");
        });

    // then should work like get
    assertThat(values).containsExactly("be good");
    assertThat(keys).containsExactly("and");
  }

  @Test
  public void shouldAllowSingleNestedWhileEqualPrefix() {
    // given
    upsertKeyValuePair("and", "be good");
    key.wrapString("and");

    // when
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          columnFamily.whileEqualPrefix(key, (k, v) -> {});
        });

    // then no exception is thrown
  }

  @Test
  public void shouldThrowExceptionOnMultipleNestedWhileEqualPrefix() {
    // given
    upsertKeyValuePair("and", "be good");
    key.wrapString("and");

    // when
    assertThatThrownBy(
            () ->
                columnFamily.whileEqualPrefix(
                    key,
                    (key, value) -> {
                      columnFamily.whileEqualPrefix(
                          key,
                          (k, v) -> {
                            columnFamily.whileEqualPrefix(key, (k2, v2) -> {});
                          });
                    }))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected error occurred during zeebe db transaction operation.")
        .hasStackTraceContaining(
            "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
  }

  private void upsertKeyValuePair(final String key, final String value) {
    this.key.wrapString(key);
    this.value.wrapString(value);
    columnFamily.upsert(this.key, this.value);
  }
}
