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

  public static SharedRocksDbResources allocateSharedCache(
      final BrokerCfg brokerCfg, final MeterRegistry meterRegistry, final int partitionsPerBroker) {
    final var rocksdbCfg = brokerCfg.getExperimental().getRocksdb();
    final long rocksDbMemoryLimit = getMemoryLimitBytes(rocksdbCfg, partitionsPerBroker);
    final var memoryAllocationStrategy = rocksdbCfg.getMemoryAllocationStrategy();
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

    RocksDbSharedCacheMetrics.registerAllocationStrategy(meterRegistry, memoryAllocationStrategy);

    final SharedRocksDbResources rocksDbResources =
        SharedRocksDbResources.allocate(rocksDbMemoryLimit);

    LOGGER.info(
        "Allocating {} bytes ({} MB) for RocksDB memory Limit (with {} MB cache size), with memory allocation strategy: {}. "
            + "Total system memory: {} bytes ({} MB). Partitions per broker: {}",
        rocksDbResources.memoryLimit(),
        rocksDbResources.memoryLimit() / (1024 * 1024),
        rocksDbResources.blockCacheSize() / (1024 * 1024),
        memoryAllocationStrategy,
        totalMemorySize,
        totalMemorySize / (1024 * 1024),
        partitionsPerBroker);

    return rocksDbResources;
  }

  public static long getMemoryLimitBytes(
      final RocksdbCfg rocksdbCfg, final int partitionsPerBrokerCount) {

    return switch (rocksdbCfg.getMemoryAllocationStrategy()) {
      case BROKER -> rocksdbCfg.getMemoryLimit().toBytes();
      case FRACTION -> getFixedMemoryPercentage(rocksdbCfg.getMemoryFraction());
      // in case of PARTITION or null, we allocate per partition
      default -> rocksdbCfg.getMemoryLimit().toBytes() * partitionsPerBrokerCount;
    };
  }

  static long getFixedMemoryPercentage(final double memoryFraction) {
    // get total memory from the OS bean.
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    return Math.round(totalMemorySize * memoryFraction);
  }

  public static void validateRocksDbMemory(
      final RocksdbCfg rocksdbCfg, final int partitionsPerBrokerCount) {
    final long blockCacheBytes = getMemoryLimitBytes(rocksdbCfg, partitionsPerBrokerCount);

    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

    if (rocksdbCfg.getMemoryAllocationStrategy() == MemoryAllocationStrategy.FRACTION) {
      // check that memoryFraction is between 0 and 1 and warn if it is too high
      warnIfTooHighFraction(rocksdbCfg);
    } else {
      // for strategies other than FRACTION which are static sizes, we check if maxMemoryFraction is
      // correctly set, and if so, we validate the allocated memory does not go above the threshold.
      validateMaxMemoryFraction(rocksdbCfg, totalMemorySize, blockCacheBytes);
      // validate that the allocated memory does not exceed total system memory.
      validateMemoryDoesNotExceedSystemMemory(
          rocksdbCfg, totalMemorySize, blockCacheBytes, partitionsPerBrokerCount);
    }

    // validate that each partition has at least the minimum required memory
    if (blockCacheBytes / partitionsPerBrokerCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsPerBrokerCount));
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

  static void validateMemoryDoesNotExceedSystemMemory(
      final RocksdbCfg rocksdbCfg,
      final long totalMemorySize,
      final long blockCacheBytes,
      final int partitionsCount) {
    if (blockCacheBytes > totalMemorySize) {
      final String configHint =
          switch (rocksdbCfg.getMemoryAllocationStrategy()) {
            case BROKER, PARTITION ->
                "Consider reducing the value of CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MEMORYLIMIT.";
            case FRACTION ->
                throw new IllegalStateException(
                    "Unexpected value: FRACTION should be within [0,1]");
          };
      throw new IllegalArgumentException(
          String.format(
              "Requested RocksDB memory (%d bytes / %d MB) exceeds total system memory (%d bytes / %d MB). "
                  + "Memory allocation strategy: %s. Partitions count: %d. %s",
              blockCacheBytes,
              blockCacheBytes / (1024 * 1024),
              totalMemorySize,
              totalMemorySize / (1024 * 1024),
              rocksdbCfg.getMemoryAllocationStrategy(),
              partitionsCount,
              configHint));
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
