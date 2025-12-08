/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import java.io.File;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DbTenantAwareKeyColumnFamilyTest {

  @TempDir public File temporaryFolder;
  private DefaultZeebeDbFactory.ZeebeDbFactoryResources<DefaultColumnFamily> dbFactoryResources;
  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbTenantAwareKey<DbLong>, DbString> columnFamily;
  private ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>>, DbString>
      compositeColumnFamily;
  private DbString tenantKey;
  private DbLong firstKey;
  private DbLong secondKey;
  private DbCompositeKey<DbLong, DbLong> compositeKey;
  private DbString value;
  private DbTenantAwareKey<DbLong> tenantAwareKey;
  private DbTenantAwareKey<DbCompositeKey<DbLong, DbLong>> compositeTenantAwareKey;

  @BeforeEach
  void beforeEach() throws Exception {
    dbFactoryResources = DefaultZeebeDbFactory.getDefaultFactoryResources();
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = dbFactoryResources.factory;
    zeebeDb = dbFactory.createDb(temporaryFolder);

    tenantKey = new DbString();
    firstKey = new DbLong();
    tenantAwareKey = new DbTenantAwareKey<>(tenantKey, firstKey, PlacementType.SUFFIX);

    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    compositeTenantAwareKey = new DbTenantAwareKey<>(tenantKey, compositeKey, PlacementType.SUFFIX);

    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), tenantAwareKey, value);
    compositeColumnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeTenantAwareKey, value);
  }

  @AfterEach
  void afterEach() throws Exception {
    if (zeebeDb != null) {
      zeebeDb.close();
    }
    if (dbFactoryResources != null) {
      dbFactoryResources.close();
    }
  }

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
  void shouldUpsertValueForMultipleTenants() {
    // given
    final String tenant = "tenant";
    final String otherTenant = "otherTenant";
    tenantKey.wrapString(tenant);
    firstKey.wrapLong(1);
    value.wrapString("foo");

    // when
    columnFamily.upsert(tenantAwareKey, value);
    tenantKey.wrapString(otherTenant);
    columnFamily.upsert(tenantAwareKey, value);
    value.wrapString("bar");

    // then
    final var keys = new ArrayList<>();
    final var values = new ArrayList<>();
    final var tenants = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.wrappedKey().getValue());
          values.add(value.toString());
          tenants.add(key.tenantKey().toString());
        });

    // then
    assertThat(keys).containsOnly(1L);
    assertThat(tenants).containsExactly("tenant", "otherTenant");
    assertThat(values).containsOnly("foo");
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

  @Test
  void shouldUseForeachValue() {
    // given
    upsertKeyValuePair(123L, "tenantId", "foo");
    upsertKeyValuePair(124L, "tenantId", "bar");
    upsertKeyValuePair(125L, "otherTenantId", "baz");
    upsertKeyValuePair(777L, "tenantId", "jackpot");
    upsertKeyValuePair(888L, "otherTenantId", "lastOne");

    // when
    final var values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values).containsExactly("foo", "bar", "baz", "jackpot", "lastOne");
  }

  @Test
  void shouldUseForeachPair() {
    // given
    upsertKeyValuePair(123L, "tenantId", "foo");
    upsertKeyValuePair(124L, "tenantId", "bar");
    upsertKeyValuePair(125L, "otherTenantId", "baz");
    upsertKeyValuePair(777L, "tenantId", "jackpot");
    upsertKeyValuePair(888L, "otherTenantId", "lastOne");

    // when
    final var keys = new ArrayList<>();
    final var values = new ArrayList<>();
    final var tenants = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.wrappedKey().getValue());
          values.add(value.toString());
          tenants.add(key.tenantKey().toString());
        });

    // then
    assertThat(keys).containsExactly(123L, 124L, 125L, 777L, 888L);
    assertThat(values).containsExactly("foo", "bar", "baz", "jackpot", "lastOne");
    assertThat(tenants)
        .containsExactly("tenantId", "tenantId", "otherTenantId", "tenantId", "otherTenantId");
  }

  @Test
  void shouldUseWhileTrue() {
    // given
    upsertKeyValuePair(123L, "tenantId", "foo");
    upsertKeyValuePair(777L, "tenantId", "jackpot");
    upsertKeyValuePair(124L, "tenantId", "bar");
    upsertKeyValuePair(888L, "otherTenantId", "lastOne");
    upsertKeyValuePair(125L, "otherTenantId", "baz");

    // when
    final var keys = new ArrayList<>();
    final var values = new ArrayList<>();
    final var tenants = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.wrappedKey().getValue());
          values.add(value.toString());
          tenants.add(key.tenantKey().toString());

          return key.wrappedKey().getValue() != 125L;
        });

    // then
    assertThat(keys).containsExactly(123L, 124L, 125L);
    assertThat(values).containsExactly("foo", "bar", "baz");
    assertThat(tenants).containsExactly("tenantId", "tenantId", "otherTenantId");
  }

  @Test
  void shouldUseWhileTrueWithStartAt() {
    // given
    upsertKeyValuePair(123L, "tenantId", "foo");
    upsertKeyValuePair(777L, "tenantId", "jackpot");
    upsertKeyValuePair(124L, "tenantId", "bar");
    upsertKeyValuePair(888L, "otherTenantId", "lastOne");
    upsertKeyValuePair(125L, "otherTenantId", "baz");
    final var startAtWrappedKey = new DbLong();
    startAtWrappedKey.wrapLong(124L);
    tenantKey.wrapString("tenantId");
    final var startAt = new DbTenantAwareKey<>(tenantKey, startAtWrappedKey, PlacementType.SUFFIX);

    // when
    final var keys = new ArrayList<>();
    final var values = new ArrayList<>();
    final var tenants = new ArrayList<>();
    columnFamily.whileTrue(
        startAt,
        (key, value) -> {
          keys.add(key.wrappedKey().getValue());
          values.add(value.toString());
          tenants.add(key.tenantKey().toString());

          return key.wrappedKey().getValue() != 125L;
        });

    // then
    assertThat(keys).containsExactly(124L, 125L);
    assertThat(values).containsExactly("bar", "baz");
    assertThat(tenants).containsExactly("tenantId", "otherTenantId");
  }

  @Test
  void shouldUseWhileEqualPrefix() {
    // given
    upsertCompositeKeyValuePair(123L, 111L, "tenantId", "foo");
    upsertCompositeKeyValuePair(321L, 333L, "tenantId", "jackpot");
    upsertCompositeKeyValuePair(123L, 333L, "tenantId", "bar");
    upsertCompositeKeyValuePair(321L, 222L, "otherTenantId", "lastOne");
    upsertCompositeKeyValuePair(123L, 222L, "otherTenantId", "baz");
    final var prefix = new DbLong();
    prefix.wrapLong(123L);

    // when
    final var firstKeys = new ArrayList<>();
    final var secondKeys = new ArrayList<>();
    final var values = new ArrayList<>();
    final var tenants = new ArrayList<>();
    compositeColumnFamily.whileEqualPrefix(
        prefix,
        (key, value) -> {
          firstKeys.add(key.wrappedKey().first().getValue());
          secondKeys.add(key.wrappedKey().second().getValue());
          values.add(value.toString());
          tenants.add(key.tenantKey().toString());
        });

    // then
    assertThat(firstKeys).containsExactly(123L, 123L, 123L);
    assertThat(secondKeys).containsExactly(111L, 222L, 333L);
    assertThat(values).containsExactly("foo", "baz", "bar");
    assertThat(tenants).containsExactly("tenantId", "otherTenantId", "tenantId");
  }

  private void upsertKeyValuePair(final long key, final String tenantId, final String value) {
    firstKey.wrapLong(key);
    this.value.wrapString(value);
    tenantKey.wrapString(tenantId);
    columnFamily.upsert(tenantAwareKey, this.value);
  }

  private void upsertCompositeKeyValuePair(
      final long firstKey, final long secondKey, final String tenantId, final String value) {
    this.firstKey.wrapLong(firstKey);
    this.secondKey.wrapLong(secondKey);
    this.value.wrapString(value);
    tenantKey.wrapString(tenantId);
    compositeColumnFamily.upsert(compositeTenantAwareKey, this.value);
  }
}
