/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Plain RocksDB factory with the consistency checks on, as the engine tests run it. */
final class RocksDbTestFactory {

  private RocksDbTestFactory() {}

  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeRocksDbFactory<ColumnFamilyType> create() {
    final var rocksDbConfiguration = new RocksDbConfiguration();
    rocksDbConfiguration.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    rocksDbConfiguration.setMemoryLimit(512 * 1024 * 1024);
    return new ZeebeRocksDbFactory<>(
        rocksDbConfiguration,
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE),
        SimpleMeterRegistry::new);
  }
}
