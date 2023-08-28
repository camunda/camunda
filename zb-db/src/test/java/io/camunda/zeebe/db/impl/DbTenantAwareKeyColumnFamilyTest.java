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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DbTenantAwareKeyColumnFamilyTest {

  @TempDir public File temporaryFolder;
  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();
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
    zeebeDb = dbFactory.createDb(temporaryFolder);

    tenantKey = new DbString();
    firstKey = new DbLong();
    tenantAwareKey = new DbTenantAwareKey<>(tenantKey, firstKey);

    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    compositeTenantAwareKey = new DbTenantAwareKey<>(tenantKey, compositeKey);

    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), tenantAwareKey, value);
    compositeColumnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeTenantAwareKey, value);
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

    // zbLong and value are referencing the same object
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

    // zbLong and value are referencing the same object
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

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("bar");
  }

  @Test
  void shouldDeleteValue() {
    // given
    upsertKeyValuePair(123L, "foo", "tenantId");

    // when
    columnFamily.deleteExisting(tenantAwareKey);

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNull();
  }
}
