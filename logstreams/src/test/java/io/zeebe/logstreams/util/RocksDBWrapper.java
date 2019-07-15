/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.util;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbString;
import io.zeebe.db.impl.DefaultColumnFamily;

public class RocksDBWrapper {

  private DbString key;
  private DbLong value;
  private ColumnFamily<DbString, DbLong> defaultColumnFamily;

  public void wrap(ZeebeDb<DefaultColumnFamily> db) {
    key = new DbString();
    value = new DbLong();
    defaultColumnFamily =
        db.createColumnFamily(DefaultColumnFamily.DEFAULT, db.createContext(), key, value);
  }

  public int getInt(String key) {
    this.key.wrapString(key);
    final DbLong zbLong = defaultColumnFamily.get(this.key);
    return zbLong != null ? (int) zbLong.getValue() : -1;
  }

  public void putInt(String key, int value) {
    this.key.wrapString(key);
    this.value.wrapLong(value);
    defaultColumnFamily.put(this.key, this.value);
  }

  public boolean mayExist(String key) {
    this.key.wrapString(key);
    return defaultColumnFamily.exists(this.key);
  }
}
