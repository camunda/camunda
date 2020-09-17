/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import java.util.Properties;

public final class RocksdbCfg {

  private Properties columnFamilyOptions;

  public Properties getColumnFamilyOptions() {
    if (columnFamilyOptions == null) {
      return new Properties();
    }
    return columnFamilyOptions;
  }

  public void setColumnFamilyOptions(final Properties columnFamilyOptions) {
    this.columnFamilyOptions = columnFamilyOptions;
  }
}
