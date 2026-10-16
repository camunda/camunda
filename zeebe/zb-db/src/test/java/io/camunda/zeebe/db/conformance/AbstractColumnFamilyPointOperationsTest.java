/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbLong;
import org.junit.Test;

/**
 * Point-operation (insert, update, get, delete, count) conformance tests for the basic {@link
 * io.camunda.zeebe.db.ColumnFamily} contract. A backend without iteration support can run this
 * class on its own.
 */
public abstract class AbstractColumnFamilyPointOperationsTest extends AbstractColumnFamilyTestBase {

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
  public void shouldGetWithSupplierWhenKeyExists() {
    // given
    upsertKeyValuePair(123, 456);

    // when
    final var result = columnFamily.get(key, DbLong::new);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(456);
  }

  @Test
  public void shouldReturnNullWithSupplierWhenKeyDoesNotExist() {
    // given
    key.wrapLong(123);

    // when
    final var result = columnFamily.get(key, DbLong::new);

    // then
    assertThat(result).isNull();
  }

  @Test
  public void shouldReturnIndependentValueWithSupplier() {
    // given
    upsertKeyValuePair(123, 456);

    // when
    final DbLong result1 = columnFamily.get(key, DbLong::new);
    final DbLong result2 = columnFamily.get(key, DbLong::new);

    // then -- verify they are different instances
    assertThat(result1).isNotSameAs(result2);
  }

  @Test
  public void shouldReturnImmutableValueWithSupplier() {
    // given
    upsertKeyValuePair(1, 2);
    upsertKeyValuePair(3, 4);

    // when
    key.wrapLong(1);
    final DbLong result1 = columnFamily.get(key, DbLong::new);
    key.wrapLong(3);
    final DbLong result2 = columnFamily.get(key, DbLong::new);

    // then -- verify they are different instances
    assertThat(result1.getValue()).isEqualTo(2);
    assertThat(result2.getValue()).isEqualTo(4);
  }
}
