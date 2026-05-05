/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.function.Supplier;

/**
 * Factory for creating {@link InMemoryZeebeDb} instances. This factory ignores the {@code pathName}
 * argument since the database is purely in-memory; the path is only used for snapshot recovery if a
 * previous snapshot exists.
 */
public final class InMemoryZeebeDbFactory<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyType> {

  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final AccessMetricsConfiguration accessMetricsConfiguration;
  private final Supplier<MeterRegistry> meterRegistryFactory;

  public InMemoryZeebeDbFactory() {
    this(
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new);
  }

  public InMemoryZeebeDbFactory(
      final ConsistencyChecksSettings consistencyChecksSettings,
      final AccessMetricsConfiguration accessMetricsConfiguration,
      final Supplier<MeterRegistry> meterRegistryFactory) {
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.accessMetricsConfiguration = accessMetricsConfiguration;
    this.meterRegistryFactory = meterRegistryFactory;
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName, final boolean avoidFlush) {
    return createDb(pathName);
  }

  @Override
  public ZeebeDb<ColumnFamilyType> createDb(final File pathName) {
    final var db =
        new InMemoryZeebeDb<ColumnFamilyType>(
            consistencyChecksSettings, accessMetricsConfiguration, meterRegistryFactory.get());
    // Recover from snapshot if one exists at the given path
    if (pathName != null && pathName.exists()) {
      InMemoryDbSnapshotSupport.readSnapshot(db.committedData, pathName);
    }
    return db;
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File path) {
    // For in-memory, a snapshot-only DB is just a regular DB loaded from a snapshot.
    return createDb(path);
  }
}
