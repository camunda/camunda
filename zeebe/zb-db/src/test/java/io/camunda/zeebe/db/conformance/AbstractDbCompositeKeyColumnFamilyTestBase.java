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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Shared fixture for the DbCompositeKeyColumnFamily conformance tests.
 *
 * <p>A backend runs the suite by extending a concrete subclass and supplying {@link #factory()}.
 */
public abstract class AbstractDbCompositeKeyColumnFamilyTestBase {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  protected ZeebeDb<DefaultColumnFamily> zeebeDb;
  protected ColumnFamily<DbCompositeKey<DbString, DbLong>, DbString> columnFamily;
  protected DbString firstKey;
  protected DbLong secondKey;
  protected DbCompositeKey<DbString, DbLong> compositeKey;
  protected DbString value;

  protected abstract ZeebeDbFactory<DefaultColumnFamily> factory();

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = factory().createDb(pathName);

    firstKey = new DbString();
    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeKey, value);
  }

  protected void upsertKeyValuePair(
      final String firstKey, final long secondKey, final String value) {
    this.firstKey.wrapString(firstKey);
    this.secondKey.wrapLong(secondKey);

    this.value.wrapString(value);
    columnFamily.upsert(compositeKey, this.value);
  }
}
