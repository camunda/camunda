/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Shared fixture for the DbTransaction conformance tests.
 *
 * <p>A backend runs the suite by extending a concrete subclass and supplying {@link #factory()}.
 */
public abstract class AbstractDbTransactionTestBase {

  protected abstract <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory();

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  protected TransactionContext transactionContext;

  protected ColumnFamily<DbLong, DbLong> oneColumnFamily;
  protected ColumnFamily<DbLong, DbLong> twoColumnFamily;
  protected ColumnFamily<DbLong, DbLong> threeColumnFamily;

  protected DbLong oneKey;
  protected DbLong oneValue;
  protected DbLong twoValue;
  protected DbLong twoKey;
  protected DbLong threeKey;
  protected DbLong threeValue;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    final ZeebeDb<ColumnFamilies> zeebeDb = this.<ColumnFamilies>factory().createDb(pathName);
    transactionContext = zeebeDb.createContext();

    oneKey = new DbLong();
    oneValue = new DbLong();
    oneColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.ONE, transactionContext, oneKey, oneValue);

    twoKey = new DbLong();
    twoValue = new DbLong();
    twoColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.TWO, transactionContext, twoKey, twoValue);

    threeKey = new DbLong();
    threeValue = new DbLong();
    threeColumnFamily =
        zeebeDb.createColumnFamily(ColumnFamilies.THREE, transactionContext, threeKey, threeValue);
  }

  private enum ColumnFamilies implements EnumValue, ScopedColumnFamily {
    DEFAULT, // rocksDB needs a default column family
    ONE,
    TWO,
    THREE;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
