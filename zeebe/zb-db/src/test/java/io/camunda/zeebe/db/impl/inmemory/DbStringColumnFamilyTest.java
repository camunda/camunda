/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DbStringColumnFamilyTest {

  private final InMemoryDbFactory<DefaultColumnFamily> dbFactory = new InMemoryDbFactory<>();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbString, DbString> columnFamily;
  private DbString key;
  private DbString value;

  @BeforeEach
  void setup() {
    zeebeDb = dbFactory.createDb();

    key = new DbString();
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  void shouldPutValue() {
    // given
    key.wrapString("foo");
    value.wrapString("baring");

    // when
    columnFamily.upsert(key, value);
    value.wrapString("yes");

    // then
    final DbString dbValue = columnFamily.get(key);

    assertThat(dbValue).isNotNull();
    assertThat(dbValue.toString()).isEqualTo("baring");

    // dbValue and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }

  @Test
  void shouldUseForeachValue() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

    // when
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string", "as you know");
  }

  @Test
  void shouldUseForeachPair() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

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
  void shouldUseWhileTrue() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("world");
        });

    // then
    assertThat(values).containsExactly("baring", "world");
    assertThat(keys).containsExactly("foo", "hello");
  }

  @Test
  void shouldUseWhileEqualPrefix() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("and", "be good");

    // when
    key.wrapString("an");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return true;
        });

    // then should be empty
    // since whileEqual should work only for composite keys
    // DbString writes length of the string before the content
    // -> that is why prefix search will not work
    assertThat(values).isEmpty();
    assertThat(keys).isEmpty();
  }

  @Test
  void shouldUseWhileEqualPrefixLikeGet() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("and", "be good");

    // when
    key.wrapString("and");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return true;
        });

    // then should work like get
    assertThat(values).containsExactly("be good");
    assertThat(keys).containsExactly("and");
  }

  @Test
  void shouldAllowSingleNestedWhileEqualPrefix() {
    // given
    putKeyValuePair("and", "be good");
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
  void shouldThrowExceptionOnMultipleNestedWhileEqualPrefix() {
    // given
    putKeyValuePair("and", "be good");
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
            "Currently nested prefix iterations of this depth are not supported! This will cause unexpected behavior.");
  }

  private void putKeyValuePair(final String key, final String value) {
    this.key.wrapString(key);
    this.value.wrapString(value);
    columnFamily.upsert(this.key, this.value);
  }
}
