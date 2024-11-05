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
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class InMemoryZeebeDbColumnFamilyTest {

  private final InMemoryDbFactory<DefaultColumnFamily> dbFactory = new InMemoryDbFactory<>();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private DbLong key;
  private DbLong value;

  @BeforeEach
  void setup() {
    zeebeDb = dbFactory.createDb();

    key = new DbLong();
    value = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  void shouldInsertValue() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);

    // when
    columnFamily.insert(key, value);
    value.wrapLong(221);

    // then
    final DbLong zbLong = columnFamily.get(key);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.getValue()).isEqualTo(255);

    // zbLong and value are referencing the same object
    assertThat(value.getValue()).isEqualTo(255);
  }

  @Test
  void shouldThrowExceptionWhenInsertingDuplicateKey() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);
    columnFamily.insert(key, value);

    // when
    value.wrapLong(221);

    // then
    assertThatThrownBy(() -> columnFamily.insert(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key DbLong{1213} in ColumnFamily DEFAULT already exists");
  }

  @Test
  void shouldReturnNullIfNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final DbLong zbLong = columnFamily.get(key);

    // then
    assertThat(zbLong).isNull();
  }

  @Test
  void shouldInsertMultipleValues() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    insertKeyValuePair(456789, 12345);
    value.wrapLong(221);

    // then
    key.wrapLong(1213);
    DbLong longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(255);

    key.wrapLong(456789);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(12345);
  }

  @Test
  void shouldInsertAndGetMultipleValues() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    DbLong longValue = columnFamily.get(key);
    insertKeyValuePair(456789, 12345);
    value.wrapLong(221);

    // then
    assertThat(longValue.getValue()).isEqualTo(221);
    key.wrapLong(1213);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(255);

    key.wrapLong(456789);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(12345);
  }

  @Test
  void shouldUpdateValue() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);
    columnFamily.insert(key, value);

    // when
    value.wrapLong(221);
    columnFamily.update(key, value);

    // then
    assertThat(columnFamily.get(key).getValue()).isEqualTo(221);
  }

  @Test
  void shouldThrowExceptionWhenUpdatingNonExistingValue() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);

    // when then
    assertThatThrownBy(() -> columnFamily.update(key, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessage("Key DbLong{1213} in ColumnFamily DEFAULT does not exist");
  }

  @Test
  void shouldUpsertNonExistingKey() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);

    // when
    columnFamily.upsert(key, value);

    // then
    assertThat(columnFamily.get(key).getValue()).isEqualTo(255);
  }

  @Test
  void shouldUpsertExistingKey() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);
    columnFamily.insert(key, value);

    // when
    value.wrapLong(221);
    columnFamily.upsert(key, value);

    // then
    assertThat(columnFamily.get(key).getValue()).isEqualTo(221);
  }

  @Test
  void shouldCheckForExistence() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isTrue();
  }

  @Test
  void shouldNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isFalse();
  }

  @Test
  void shouldDeleteExisting() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    columnFamily.deleteExisting(key);

    // then
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isFalse();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNull();
  }

  @Test
  void shouldNotDeleteExistingDifferentKey() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    key.wrapLong(700);
    columnFamily.deleteIfExists(key);

    // then
    key.wrapLong(1213);
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isTrue();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNotNull();
    assertThat(zbLong.getValue()).isEqualTo(255);
  }

  @Test
  void shouldUseForeachValue() {
    // given
    insertKeyValuePair(4567, 123);
    insertKeyValuePair(6734, 921);
    insertKeyValuePair(1213, 255);
    insertKeyValuePair(1, Short.MAX_VALUE);
    insertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.getValue()));

    // then
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  void shouldUseForeachPair() {
    // given
    insertKeyValuePair(4567, 123);
    insertKeyValuePair(6734, 921);
    insertKeyValuePair(1213, 255);
    insertKeyValuePair(1, Short.MAX_VALUE);
    insertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L, 6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  void shouldDeleteExistingOnForeachPair() {
    // given
    insertKeyValuePair(4567, 123);
    insertKeyValuePair(6734, 921);
    insertKeyValuePair(1213, 255);
    insertKeyValuePair(1, Short.MAX_VALUE);
    insertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.forEach((key, value) -> columnFamily.deleteExisting(key));

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).isEmpty();
    assertThat(values).isEmpty();
    key.wrapLong(4567L);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(6734);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1213);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(Short.MAX_VALUE);
    assertThat(columnFamily.exists(key)).isFalse();
  }

  @Test
  void shouldUseWhileTrue() {
    // given
    insertKeyValuePair(4567, 123);
    insertKeyValuePair(6734, 921);
    insertKeyValuePair(1213, 255);
    insertKeyValuePair(1, Short.MAX_VALUE);
    insertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L);
  }

  @Test
  void shouldDeleteExistingWhileTrue() {
    // given
    insertKeyValuePair(4567, 123);
    insertKeyValuePair(6734, 921);
    insertKeyValuePair(1213, 255);
    insertKeyValuePair(1, Short.MAX_VALUE);
    insertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.whileTrue(
        (key, value) -> {
          columnFamily.deleteExisting(key);
          return key.getValue() != 4567;
        });

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly(921L, 1L);
  }

  @Test
  void shouldDeleteKeyIfExists() {
    // given
    insertKeyValuePair(1213, 255);

    // when
    columnFamily.deleteIfExists(key);

    // then
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isFalse();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNull();
  }

  @Test
  void shouldDeleteKeyIfNotExists() {
    // given

    // when
    columnFamily.deleteIfExists(key);

    // then
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isFalse();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNull();
  }

  @Test
  void shouldCheckIfEmpty() {
    assertThat(columnFamily.isEmpty()).isTrue();

    insertKeyValuePair(1, 10);
    assertThat(columnFamily.isEmpty()).isFalse();

    columnFamily.deleteExisting(key);
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  private void insertKeyValuePair(final int key, final int value) {
    this.key.wrapLong(key);
    this.value.wrapLong(value);
    columnFamily.insert(this.key, this.value);
  }
}
