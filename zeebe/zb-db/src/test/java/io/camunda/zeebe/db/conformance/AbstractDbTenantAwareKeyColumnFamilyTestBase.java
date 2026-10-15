/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared fixture for the DbTenantAwareKeyColumnFamily conformance tests.
 *
 * <p>A backend runs the suite by extending a concrete subclass and supplying {@link #factory()}.
 */
public abstract class AbstractDbTenantAwareKeyColumnFamilyTestBase {

  protected abstract ZeebeDbFactory<DefaultColumnFamily> factory();

  @TempDir public File temporaryFolder;
  protected ZeebeDb<DefaultColumnFamily> zeebeDb;
  protected ColumnFamily<DbTenantAwareKey<DbLong>, DbString> columnFamily;
  protected DbString tenantKey;
  protected DbLong firstKey;

  protected DbString value;

  protected DbTenantAwareKey<DbLong> tenantAwareKey;

  @BeforeEach
  void beforeEach() throws Exception {
    zeebeDb = factory().createDb(temporaryFolder);

    tenantKey = new DbString();
    firstKey = new DbLong();
    tenantAwareKey = new DbTenantAwareKey<>(tenantKey, firstKey, PlacementType.SUFFIX);

    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), tenantAwareKey, value);
  }

  protected void upsertKeyValuePair(final long key, final String tenantId, final String value) {
    firstKey.wrapLong(key);
    this.value.wrapString(value);
    tenantKey.wrapString(tenantId);
    columnFamily.upsert(tenantAwareKey, this.value);
  }
}
