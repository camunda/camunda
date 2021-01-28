/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
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
    return defaultFactory(ZbColumnFamilies.class, userProvidedColumnFamilyOptions);
  }

  /**
   * Returns the default zeebe database factory which is used in the broker.
   *
   * @param <ColumnFamilyNames> the type of the enum
   * @param columnFamilyNamesClass the enum class, which contains the column family names
   * @return the created zeebe database factory
   */
  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeDbFactory<ColumnFamilyNames> defaultFactory(
          final Class<ColumnFamilyNames> columnFamilyNamesClass) {
    return defaultFactory(columnFamilyNamesClass, new Properties());
  }

  /**
   * Returns the default zeebe database factory which is used in the broker.
   *
   * @param <ColumnFamilyNames> the type of the enum
   * @param columnFamilyNamesClass the enum class, which contains the column family names
   * @param userProvidedColumnFamilyOptions additional column family options
   * @return the created zeebe database factory
   */
  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeDbFactory<ColumnFamilyNames> defaultFactory(
          final Class<ColumnFamilyNames> columnFamilyNamesClass,
          final Properties userProvidedColumnFamilyOptions) {
    // one place to replace the zeebe database implementation
    return ZeebeRocksDbFactory.newFactory(columnFamilyNamesClass, userProvidedColumnFamilyOptions);
  }
}
