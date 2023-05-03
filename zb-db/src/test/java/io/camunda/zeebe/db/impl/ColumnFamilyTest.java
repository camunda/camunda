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
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ColumnFamilyTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private DbLong key;
  private DbLong value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    key = new DbLong();
    value = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  public void shouldInsertValue() {
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
  public void shouldReturnNullIfNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final DbLong zbLong = columnFamily.get(key);

    // then
    assertThat(zbLong).isNull();
  }

  @Test
  public void shouldReturnNullIfNotValid() {
    // given
    key.wrapLong(-1);

    // when
    final DbLong zbLong = columnFamily.get(key);

    // then
    assertThat(key.isValid()).isFalse();
    assertThat(zbLong).isNull();
  }

  @Test
  public void shouldPutMultipleValues() {
    // given
    upsertKeyValuePair(1213, 255);

    // when
    upsertKeyValuePair(456789, 12345);
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
  public void shouldPutAndGetMultipleValues() {
    // given
    upsertKeyValuePair(1213, 255);

    // when
    DbLong longValue = columnFamily.get(key);
    upsertKeyValuePair(456789, 12345);
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
  public void shouldCheckForExistence() {
    // given
    upsertKeyValuePair(1213, 255);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isTrue();
  }

  @Test
  public void shouldNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isFalse();
  }

  @Test
  public void shouldDelete() {
    // given
    upsertKeyValuePair(1213, 255);

    // when
    columnFamily.deleteExisting(key);

    // then
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isFalse();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNull();
  }

  @Test
  public void shouldNotDeleteDifferentKey() {
    // given
    upsertKeyValuePair(1213, 255);

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
  public void shouldUseForeachValue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.getValue()));

    // then
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

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
  public void shouldDeleteOnForeachPair() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

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
  public void shouldUseWhileTrue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

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
  public void shouldDeleteWhileTrue() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

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
  public void shouldUseWhileTrueWithStartAt() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1213);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldUseWhileTrueWithStartAtMissingKey() {
    // given
    final var startAt = new DbLong();
    startAt.wrapLong(1212);

    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);
    upsertKeyValuePair(1, Short.MAX_VALUE);
    upsertKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldUseWhileTrueWithNullStartAt() {
    // given
    upsertKeyValuePair(4567, 123);
    upsertKeyValuePair(6734, 921);
    upsertKeyValuePair(1213, 255);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        null,
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1213L, 4567L);
    assertThat(values).containsExactly(255L, 123L);
  }

  @Test
  public void shouldCheckIfEmpty() {
    assertThat(columnFamily.isEmpty()).isTrue();

    upsertKeyValuePair(1, 10);
    assertThat(columnFamily.isEmpty()).isFalse();

    columnFamily.deleteExisting(key);
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  @Test
  public void shouldThrowOnInsert() {
    key.wrapLong(1);
    value.wrapLong(10);
    columnFamily.insert(key, value);
    assertThatThrownBy(() -> columnFamily.insert(key, value))
        .hasMessageContaining("already exists")
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }

  @Test
  public void shouldThrowOnUpdate() {
    key.wrapLong(1);
    value.wrapLong(10);
    assertThatThrownBy(() -> columnFamily.update(key, value))
        .hasMessageContaining("does not exist")
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }

  @Test
  public void shouldThrowOnDeleteExisting() {
    key.wrapLong(1);
    assertThatThrownBy(() -> columnFamily.deleteExisting(key))
        .hasMessageContaining("does not exist")
        .isInstanceOf(ZeebeDbInconsistentException.class);
  }

  @Test
  public void shouldThrowOnMissingForeignKeyInKeyPosition() {
    // given
    key.wrapLong(1);
    final var foreignKey = new DbForeignKey<>(key, DefaultColumnFamily.DEFAULT);
    final var value = new DbLong();
    final var columnFamilyWithForeignKey =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), foreignKey, value);

    // then
    assertThatThrownBy(() -> columnFamilyWithForeignKey.insert(foreignKey, value))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Foreign key");
  }

  @Test
  public void shouldThrowOnMissingForeignKeyInValuePosition() {
    // given
    key.wrapLong(1);
    final var foreignKey = new DbForeignKey<>(new DbLong(), DefaultColumnFamily.DEFAULT);
    final var columnFamilyWithForeignKey =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, foreignKey);
    // then
    assertThatThrownBy(() -> columnFamilyWithForeignKey.insert(key, foreignKey))
        .isInstanceOf(ZeebeDbInconsistentException.class)
        .hasMessageContaining("Foreign key");
  }

  private void upsertKeyValuePair(final int key, final int value) {
    this.key.wrapLong(key);
    this.value.wrapLong(value);
    columnFamily.upsert(this.key, this.value);
  }
}
