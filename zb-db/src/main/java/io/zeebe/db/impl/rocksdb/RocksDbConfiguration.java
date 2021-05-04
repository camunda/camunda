/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import java.util.Properties;

public final class RocksDbConfiguration {

  private Properties columnFamilyOptions = new Properties();

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public RocksDbConfiguration setColumnFamilyOptions(final Properties columnFamilyOptions) {
    this.columnFamilyOptions = columnFamilyOptions;
    return this;
  }
}
