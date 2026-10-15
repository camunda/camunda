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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Shared fixture for the DbStringColumnFamily conformance tests.
 *
 * <p>A backend runs the suite by extending a concrete subclass and supplying {@link #factory()}.
 */
public abstract class AbstractDbStringColumnFamilyTestBase {

  protected abstract ZeebeDbFactory<DefaultColumnFamily> factory();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  protected ZeebeDb<DefaultColumnFamily> zeebeDb;
  protected ColumnFamily<DbString, DbString> columnFamily;
  protected DbString key;
  protected DbString value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = factory().createDb(pathName);

    key = new DbString();
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  protected void upsertKeyValuePair(final String key, final String value) {
    this.key.wrapString(key);
    this.value.wrapString(value);
    columnFamily.upsert(this.key, this.value);
  }
}
