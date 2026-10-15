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
import org.junit.jupiter.api.Test;

/**
 * Point-operation (insert, update, get, delete, count) conformance tests for a column family keyed
 * by a tenant-aware key. A backend without iteration support can run this class on its own.
 */
public abstract class AbstractDbTenantAwareKeyColumnFamilyPointOperationsTest
    extends AbstractDbTenantAwareKeyColumnFamilyTestBase {

  @Test
  void shouldInsertValue() {
    // given
    tenantKey.wrapString("tenant");
    firstKey.wrapLong(1);
    value.wrapString("foo");

    // when
    columnFamily.insert(tenantAwareKey, value);
    value.wrapString("bar");

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNotNull();
    assertThat(zbString.toString()).isEqualTo("foo");

    // zbString and value are referencing the same object
    assertThat(value.toString()).isEqualTo("foo");
  }

  @Test
  void shouldUpsertValue() {
    // given
    tenantKey.wrapString("tenant");
    firstKey.wrapLong(1);
    value.wrapString("foo");

    // when
    columnFamily.upsert(tenantAwareKey, value);
    value.wrapString("bar");

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNotNull();
    assertThat(zbString.toString()).isEqualTo("foo");

    // zbString and value are referencing the same object
    assertThat(value.toString()).isEqualTo("foo");
  }

  @Test
  public void shouldUpdateValue() {
    // given
    tenantKey.wrapString("tenant");
    firstKey.wrapLong(1);
    value.wrapString("foo");
    columnFamily.insert(tenantAwareKey, value);

    // when
    value.wrapString("bar");
    columnFamily.upsert(tenantAwareKey, value);
    value.wrapString("baz");

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNotNull();
    assertThat(zbString.toString()).isEqualTo("bar");

    // zbString and value are referencing the same object
    assertThat(value.toString()).isEqualTo("bar");
  }

  @Test
  void shouldDeleteValue() {
    // given
    upsertKeyValuePair(123L, "tenantId", "foo");

    // when
    columnFamily.deleteExisting(tenantAwareKey);

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNull();
  }
}
