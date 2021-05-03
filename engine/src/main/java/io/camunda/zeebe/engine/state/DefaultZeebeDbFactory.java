/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDBMetricExporter;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import java.util.Properties;
import java.util.function.BiFunction;

public final class DefaultZeebeDbFactory {

  public static final BiFunction<String, ZeebeDb<ZbColumnFamilies>, ZeebeRocksDBMetricExporter>
      DEFAULT_DB_METRIC_EXPORTER_FACTORY = ZeebeRocksDBMetricExporter::new;

  /**
   * Returns the default zeebe database factory, which is used in most of the places except for the
   * exporters.
   *
   * @return the created zeebe database factory
   */
  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    return defaultFactory(new Properties());
  }

  /**
   * Returns the default zeebe database factory, which is used in most of the places except for the
   * exporters.
   *
   * @param userProvidedColumnFamilyOptions additional column family options
   * @return the created zeebe database factory
   */
  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory(
      final Properties userProvidedColumnFamilyOptions) {
    return defaultFactory(
        new RocksDbConfiguration().setColumnFamilyOptions(userProvidedColumnFamilyOptions));
  }

  /**
   * Returns the default zeebe database factory which is used in the broker.
   *
   * @param <ColumnFamilyNames> the type of the enum
   * @param rocksDbConfiguration user provided rocks db configuration
   * @return the created zeebe database factory
   */
  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeDbFactory<ColumnFamilyNames> defaultFactory(
          final RocksDbConfiguration rocksDbConfiguration) {
    // one place to replace the zeebe database implementation
    return ZeebeRocksDbFactory.newFactory(rocksDbConfiguration);
  }
}
