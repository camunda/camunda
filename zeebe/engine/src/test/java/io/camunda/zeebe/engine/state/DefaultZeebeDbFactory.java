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
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;

public final class DefaultZeebeDbFactory {
  private static final long MINIMUM_MEMORY_BYTES = 32 * 1024 * 1024; // 32 MB
  private static final int DEFAULT_PARTITION_COUNT = 3;

  /**
   * Set to {@code true} to run engine tests against the write-behind DB. An environment variable
   * rather than a system property, so that it also reaches JMH's forked benchmark JVMs.
   */
  private static final boolean WRITE_BEHIND_ENABLED =
      Boolean.parseBoolean(System.getenv("ZEEBE_DB_WRITE_BEHIND_ENABLED"));

  /** Set to {@code true} to record RocksDB statistics (cache hits, bytes written, stalls). */
  private static final boolean ROCKSDB_STATISTICS =
      Boolean.parseBoolean(System.getenv("ZEEBE_DB_ROCKSDB_STATISTICS"));

  /**
   * Flush delay of the write-behind writer in milliseconds, default 0. The writer also flushes
   * early at the flush threshold, so raise {@code ZEEBE_DB_WRITE_BEHIND_MAX_PENDING_MB} as well for
   * the delay to take effect on a busy engine.
   */
  private static final long WRITE_BEHIND_FLUSH_DELAY_MS =
      Long.parseLong(System.getenv().getOrDefault("ZEEBE_DB_WRITE_BEHIND_FLUSH_DELAY_MS", "0"));

  /** Sets the pending bytes limit and the early flush threshold of the write-behind writer. */
  private static final long WRITE_BEHIND_MAX_PENDING_MB =
      Long.parseLong(System.getenv().getOrDefault("ZEEBE_DB_WRITE_BEHIND_MAX_PENDING_MB", "0"));

  /** Set to {@code true} to count and time the access per column family (RocksDB only). */
  private static final boolean ACCESS_METRICS =
      Boolean.parseBoolean(System.getenv("ZEEBE_DB_ACCESS_METRICS"));

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
    rocksDbConfig.setStatisticsEnabled(ROCKSDB_STATISTICS);
    final var rocksDbFactory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            rocksDbConfig,
            consistencyChecks,
            new AccessMetricsConfiguration(ACCESS_METRICS ? Kind.FINE : Kind.NONE),
            SimpleMeterRegistry::new,
            RocksDbResources.of(rocksDbConfig, new RuntimeInfo(partitions)));
    if (!WRITE_BEHIND_ENABLED) {
      return rocksDbFactory;
    }
    var writeBehindConfig =
        WriteBehindConfiguration.defaults()
            .withFlushDelay(Duration.ofMillis(WRITE_BEHIND_FLUSH_DELAY_MS));
    if (WRITE_BEHIND_MAX_PENDING_MB > 0) {
      final long bytes = WRITE_BEHIND_MAX_PENDING_MB * 1024 * 1024;
      writeBehindConfig =
          writeBehindConfig.withMaxPendingBytes(bytes).withFlushThresholdBytes(bytes);
    }
    return new WriteBehindZeebeDbFactory<>(rocksDbFactory, writeBehindConfig, "engine-test");
  }
}
