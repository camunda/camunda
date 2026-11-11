/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.RawAccessibleDb;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.nio.file.Path;

/** Opens a real RocksDB for tests of the store. */
final class RocksDbSupport {

  private RocksDbSupport() {}

  @SuppressWarnings("unchecked")
  static RawAccessibleDb<DefaultColumnFamily> open(final Path directory) {
    final var rocksConfig = new RocksDbConfiguration();
    rocksConfig.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    rocksConfig.setMemoryLimit(64 * 1024 * 1024);
    final var factory =
        new ZeebeRocksDbFactory<DefaultColumnFamily>(
            rocksConfig,
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new);
    return (RawAccessibleDb<DefaultColumnFamily>)
        factory.createDb(new File(directory.toFile(), "db"));
  }
}
