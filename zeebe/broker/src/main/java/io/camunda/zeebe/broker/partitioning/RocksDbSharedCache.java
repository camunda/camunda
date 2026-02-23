/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import com.sun.management.OperatingSystemMXBean;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDbSharedCache {
  public static final long MINIMUM_PARTITION_MEMORY_LIMIT = 32 * 1024 * 1024L;
  public static final double ADVICE_MAX_MEMORY_FRACTION = 0.5;
  private static final Logger LOGGER = LoggerFactory.getLogger(RocksDbSharedCache.class);

  static SharedRocksDbResources allocateSharedCache(
      final BrokerCfg brokerCfg, final MeterRegistry meterRegistry) {
    final int partitionsCount = brokerCfg.getCluster().getPartitionsCount();

    final var rocksdbCfg = brokerCfg.getExperimental().getRocksdb();
    final long blockCacheBytes = getBlockCacheBytes(rocksdbCfg, partitionsCount);
    final var memoryAllocationStrategy = rocksdbCfg.getMemoryAllocationStrategy();

    LOGGER.debug(
        "Allocating {} bytes for RocksDB, with memory allocation strategy: {}",
        blockCacheBytes,
        memoryAllocationStrategy);

    RocksDbSharedCacheMetrics.registerAllocationStrategy(meterRegistry, memoryAllocationStrategy);

    return SharedRocksDbResources.allocate(blockCacheBytes);
  }

  public static long getBlockCacheBytes(final RocksdbCfg rocksdbCfg, final int partitionsCount) {

    return switch (rocksdbCfg.getMemoryAllocationStrategy()) {
      case BROKER -> rocksdbCfg.getMemoryLimit().toBytes();
      case FRACTION -> getFixedMemoryPercentage(rocksdbCfg.getMemoryFraction());
      // in case of PARTITION or null, we allocate per partition
      default -> rocksdbCfg.getMemoryLimit().toBytes() * partitionsCount;
    };
  }

  static long getFixedMemoryPercentage(final double memoryFraction) {
    // get total memory from the OS bean.
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    return Math.round(totalMemorySize * memoryFraction);
  }

  public static void validateRocksDbMemory(final RocksdbCfg rocksdbCfg, final int partitionsCount) {
    final long blockCacheBytes = getBlockCacheBytes(rocksdbCfg, partitionsCount);

    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

    if (rocksdbCfg.getMemoryAllocationStrategy() == MemoryAllocationStrategy.FRACTION) {
      // check that memoryFraction is between 0 and 1 and warn if it is too high
      warnIfTooHighFraction(rocksdbCfg);
    } else {
      if (rocksdbCfg.getMemoryAllocationStrategy() == MemoryAllocationStrategy.PARTITION) {
        LOGGER.warn(
            "Note: CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MEMORYFRACTION is set to PARTITION, this configuration will be set to FRACTION by default in 8.10. If the intended goal is to use PARTITION strategy, please set it explicitly.");
      }
      // for strategies other than FRACTION which are static sizes, we check if maxMemoryFraction is
      // correctly set, and if so, we validate the allocated memory does not go above the threshold.
      validateMaxMemoryFraction(rocksdbCfg, totalMemorySize, blockCacheBytes);
    }

    // validate that each partition has at least the minimum required memory
    if (blockCacheBytes / partitionsCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsCount));
    }
  }

  private static void validateMaxMemoryFraction(
      final RocksdbCfg rocksdbCfg, final long totalMemorySize, final long blockCacheBytes) {
    final double maxMemoryFraction = rocksdbCfg.getMaxMemoryFraction();
    if (maxMemoryFraction == -1) {
      LOGGER.debug(
          "Max Memory check for RocksDB is disabled. This can be configured setting CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MAXMEMORYFRACTION with a value between 0 and 1 to set the max fraction that RocksDB can take of total RAM memory.");
      return;
    }

    if (maxMemoryFraction <= 0 || maxMemoryFraction > 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the maxMemoryFraction for RocksDB to be between 0 and 1 (exclusive of 0) or -1 (disabled), but was %s.",
              maxMemoryFraction));
    }

    final long maxRocksDbMem = (long) (totalMemorySize * maxMemoryFraction);

    if (blockCacheBytes > maxRocksDbMem
        && rocksdbCfg.getMemoryAllocationStrategy() != MemoryAllocationStrategy.FRACTION) {
      // this check does not apply for FRACTION strategy as it is calculated
      // based on available memory
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB to be below or "
                  + "equal %.2f %% of ram memory, but was %.2f %%.",
              maxMemoryFraction * 100, ((double) blockCacheBytes / totalMemorySize * 100)));
    }
  }

  private static void warnIfTooHighFraction(final RocksdbCfg rocksdbCfg) {
    if (rocksdbCfg.getMemoryFraction() <= 0.0 || rocksdbCfg.getMemoryFraction() >= 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
              rocksdbCfg.getMemoryFraction()));
    }

    if (rocksdbCfg.getMemoryFraction() >= ADVICE_MAX_MEMORY_FRACTION) {
      LOGGER.warn(
          "The configured memoryFraction for RocksDB is set to {}, which is quite high and may lead to out of memory issues for the broker.",
          rocksdbCfg.getMemoryFraction());
    }
  }
}
