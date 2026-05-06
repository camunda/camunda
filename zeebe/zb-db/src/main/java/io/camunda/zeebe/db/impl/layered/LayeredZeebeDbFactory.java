/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.function.Supplier;

/**
 * Factory for the layered ZeebeDb experiment.
 *
 * <p>Reads populate a hot in-memory layer while RocksDB remains the persistent source of truth.
 */
public final class LayeredZeebeDbFactory<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyType> {
  private static final ConsistencyChecksSettings INDIVIDUAL_DB_CONSISTENCY_SETTINGS =
      new ConsistencyChecksSettings();

  private final ZeebeRocksDbFactory<ColumnFamilyType> persistentFactory;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final AccessMetricsConfiguration accessMetricsConfiguration;

  @VisibleForTesting
  public LayeredZeebeDbFactory(
      final RocksDbConfiguration rocksDbConfiguration,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final AccessMetricsConfiguration accessMetricsConfiguration,
      final Supplier<MeterRegistry> meterRegistryFactory) {
    this(
        rocksDbConfiguration,
        consistencyChecksSettings,
        accessMetricsConfiguration,
        meterRegistryFactory,
        new SharedRocksDbResources(rocksDbConfiguration.getMemoryLimit()),
        3);
  }

  public LayeredZeebeDbFactory(
      final RocksDbConfiguration rocksDbConfiguration,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final AccessMetricsConfiguration accessMetricsConfiguration,
      final Supplier<MeterRegistry> meterRegistryFactory,
      final SharedRocksDbResources sharedRocksDbResources,
      final int partitionCount) {
    persistentFactory =
        new ZeebeRocksDbFactory<>(
            rocksDbConfiguration,
            // disabled as each zeebeDB is not consistent within itself
            INDIVIDUAL_DB_CONSISTENCY_SETTINGS,
            accessMetricsConfiguration,
            meterRegistryFactory,
            sharedRocksDbResources,
            partitionCount);
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.accessMetricsConfiguration = accessMetricsConfiguration;
  }

  public ZeebeRocksDbFactory<ColumnFamilyType> persistentFactory() {
    return persistentFactory;
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName, final boolean avoidFlush) {
    final var persistentDb = persistentFactory.createDb(pathName, avoidFlush);
    final var activeDb =
        new InMemoryZeebeDb<ColumnFamilyType>(
            INDIVIDUAL_DB_CONSISTENCY_SETTINGS,
            accessMetricsConfiguration,
            persistentDb.getMeterRegistry());
    return new LayeredZeebeDb<>(activeDb, persistentDb, consistencyChecksSettings);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    final var persistentDb = persistentFactory.createDb(pathName);
    final var activeDb =
        new InMemoryZeebeDb<ColumnFamilyType>(
            INDIVIDUAL_DB_CONSISTENCY_SETTINGS,
            accessMetricsConfiguration,
            persistentDb.getMeterRegistry());
    return new LayeredZeebeDb<>(activeDb, persistentDb, consistencyChecksSettings);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    return persistentFactory.openSnapshotOnlyDb(path);
  }
}
