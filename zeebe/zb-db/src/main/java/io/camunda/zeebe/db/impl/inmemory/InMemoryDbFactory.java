/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import java.io.File;

public class InMemoryDbFactory<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDbFactory<ColumnFamilyType> {

  public ZeebeDb<ColumnFamilyType> createDb() {
    return createDb(null);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    return new InMemoryDb<>();
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    throw new UnsupportedOperationException("Snapshots are not supported with in-memory databases");
  }
}
