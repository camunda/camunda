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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DbTenantAwareKeyColumnFamilyTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
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

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

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
  public void shouldInsertValue() {
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
  public void shouldUpsertValue() {
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
  public void shouldDeleteValue() {
    // given
    tenantKey.wrapString("tenant");
    firstKey.wrapLong(1);
    value.wrapString("foo");
    columnFamily.insert(tenantAwareKey, value);

    // when
    columnFamily.deleteExisting(tenantAwareKey);

    // then
    final DbString zbString = columnFamily.get(tenantAwareKey);

    assertThat(zbString).isNull();
  }
}
