/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;

public final class RocksDBWrapper {

  private DbString key;
  private DbLong value;
  private ColumnFamily<DbString, DbLong> defaultColumnFamily;

  public void wrap(final ZeebeDb<DefaultColumnFamily> db) {
    key = new DbString();
    value = new DbLong();
    defaultColumnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
  }

  public int getInt(final String key) {
    this.key.wrapString(key);
    final DbLong zbLong = defaultColumnFamily.get(this.key);
    return zbLong != null ? (int) zbLong.getValue() : -1;
  }

  public void putInt(final String key, final int value) {
    this.key.wrapString(key);
    this.value.wrapLong(value);
    defaultColumnFamily.put(this.key, this.value);
  }

  public boolean mayExist(final String key) {
    this.key.wrapString(key);
    return defaultColumnFamily.exists(this.key);
  }
}
