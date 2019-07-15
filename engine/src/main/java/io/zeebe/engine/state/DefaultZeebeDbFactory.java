/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;

public final class DefaultZeebeDbFactory {

  /**
   * The default zeebe database factory, which is used in most of the places except for the
   * exporters.
   */
  public static final ZeebeDbFactory<ZbColumnFamilies> DEFAULT_DB_FACTORY =
      defaultFactory(ZbColumnFamilies.class);

  /**
   * Returns the default zeebe database factory which is used in the broker.
   *
   * @param columnFamilyNamesClass the enum class, which contains the column family names
   * @param <ColumnFamilyNames> the type of the enum
   * @return the created zeebe database factory
   */
  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeDbFactory<ColumnFamilyNames> defaultFactory(
          Class<ColumnFamilyNames> columnFamilyNamesClass) {
    // one place to replace the zeebe database implementation
    return ZeebeRocksDbFactory.newFactory(columnFamilyNamesClass);
  }
}
