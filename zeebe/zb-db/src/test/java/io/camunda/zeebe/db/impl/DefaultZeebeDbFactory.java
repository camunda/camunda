/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Supplier;

public final class DefaultZeebeDbFactory {

  private static final long DEFAULT_MEMORY_LIMIT_BYTES = 512 * 1024 * 1024;

  public static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory() {
    return getDefaultFactory(SimpleMeterRegistry::new);
  }

  public static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory(
          final MemoryAllocationStrategy strategy, final long memoryLimitBytes) {
    return create(SimpleMeterRegistry::new, strategy, memoryLimitBytes);
  }

  public static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory(
          final Supplier<MeterRegistry> meterRegistry) {
    return create(meterRegistry, MemoryAllocationStrategy.BROKER, DEFAULT_MEMORY_LIMIT_BYTES);
  }

  private static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> create(
          final Supplier<MeterRegistry> meterRegistry,
          final MemoryAllocationStrategy strategy,
          final long memoryLimitBytes) {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final var rocksDbConfiguration = new RocksDbConfiguration();
    rocksDbConfiguration.setMemoryAllocationStrategy(strategy);
    rocksDbConfiguration.setMemoryLimit(memoryLimitBytes);
    return new ZeebeRocksDbFactory<>(
        rocksDbConfiguration,
        consistencyChecks,
        new AccessMetricsConfiguration(Kind.NONE),
        meterRegistry);
  }
}
