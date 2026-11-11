/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import io.camunda.zeebe.db.cache.WriteBehindZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.slf4j.LoggerFactory;

/**
 * Creates the RocksDB factory the engine tests use. With {@code
 * -Dzeebe.db.writeBehind.enabled=true}, it puts the write-behind cache in front of RocksDB, so the
 * engine tests run against the cache.
 */
public final class DefaultZeebeDbFactory {
  private static final long MINIMUM_MEMORY_BYTES = 32 * 1024 * 1024; // 32 MB
  private static final int DEFAULT_PARTITION_COUNT = 3;
  private static final String WRITE_BEHIND_ENABLED = "zeebe.db.writeBehind.enabled";

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    return defaultFactory(DEFAULT_PARTITION_COUNT);
  }

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory(final int partitions) {
    return defaultFactory(partitions, MINIMUM_MEMORY_BYTES * partitions);
  }

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory(
      final int partitions, final long memoryLimitBytes) {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final var rocksDbConfig = new RocksDbConfiguration();
    rocksDbConfig.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksDbConfig.setMemoryLimit(memoryLimitBytes);
    final var rocksDbFactory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            rocksDbConfig,
            consistencyChecks,
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new,
            RocksDbResources.of(rocksDbConfig, new RuntimeInfo(partitions)));
    if (!Boolean.getBoolean(WRITE_BEHIND_ENABLED)) {
      return rocksDbFactory;
    }
    final var defaults = WriteBehindConfiguration.DEFAULT;
    final var configuration =
        new WriteBehindConfiguration(
            defaults.cacheMaxBytes(),
            defaults.maxPendingBytes(),
            Integer.getInteger("zeebe.db.writeBehind.batchSize", defaults.batchSize()),
            Duration.ofMillis(
                Long.getLong(
                    "zeebe.db.writeBehind.maxWriteDelayMs", defaults.maxWriteDelay().toMillis())),
            defaults.snapshotCatchUpTimeout());
    LoggerFactory.getLogger("WRITE_BEHIND_BENCH").info("write-behind {}", configuration);
    final var smallerRocksDb = new RocksDbConfiguration();
    smallerRocksDb.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    smallerRocksDb.setMemoryLimit(
        memoryLimitBytes - configuration.cacheMaxBytes() - configuration.maxPendingBytes());
    final var smallerRocksDbFactory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            smallerRocksDb,
            consistencyChecks,
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new,
            RocksDbResources.of(smallerRocksDb, new RuntimeInfo(partitions)));
    return new WriteBehindZeebeDbFactory<>(smallerRocksDbFactory, configuration, consistencyChecks);
  }
}
