/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDbSharedCache {
  public static final long MINIMUM_PARTITION_MEMORY_LIMIT = 32 * 1024 * 1024L;
  private static final double ROCKSDB_OVERHEAD_FACTOR = 0.15;

  private static final Logger LOGGER = LoggerFactory.getLogger(RocksDbSharedCache.class);

  static SharedRocksDbResources allocateSharedCache(final BrokerCfg brokerCfg) {
    final int partitionsCount = brokerCfg.getCluster().getPartitionsCount();

    final long blockCacheBytes =
        getBlockCacheBytes(brokerCfg.getExperimental().getRocksdb(), partitionsCount);

    LOGGER.debug(
        "Allocating {} bytes for RocksDB, with memory allocation strategy: {}",
        blockCacheBytes,
        brokerCfg.getExperimental().getRocksdb().getMemoryAllocationStrategy());

    return SharedRocksDbResources.allocate(blockCacheBytes);
  }

  public static long getBlockCacheBytes(final RocksdbCfg rocksdbCfg, final int partitionsCount) {

    return switch (rocksdbCfg.getMemoryAllocationStrategy()) {
      case BROKER -> rocksdbCfg.getMemoryLimit().toBytes();
      case AUTO -> getAvailableMemoryCapacity();
      // in case of PARTITION or null, we allocate per partition
      default -> rocksdbCfg.getMemoryLimit().toBytes() * partitionsCount;
    };
  }

  static long getAvailableMemoryCapacity() {
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // This is the largest size the JVM Heap is allowed to grow to. -XX:MaxRAMPercentage.
    long maxHeapBytes = memoryBean.getHeapMemoryUsage().getMax();
    // Get Maximum Non-Heap Size, -XX:MaxMetaspaceSize
    long maxNonHeapBytes = memoryBean.getNonHeapMemoryUsage().getMax();
    // both return -1 if not defined.

    // if not set, assume default to 25% of total memory
    if (maxNonHeapBytes < 0) {
      maxNonHeapBytes = Math.round(0.25 * totalMemorySize);
      LOGGER.info(
          "Warning: Non-Heap limit is not set. Using an assumption of 25% of "
              + "total RAM :{} Bytes for safety, for RocksDB memory calculation. "
              + "Consider setting -XX:MaxMetaspaceSize, -XX:CompressedClassSpaceSize, and -XX:ReservedCodeCacheSize explicitly.",
          maxNonHeapBytes);
    }
    // -XX:MaxRAMPercentage is almost always set, but in case it is removed.
    if (maxHeapBytes < 0) {
      maxHeapBytes = Math.round(0.25 * totalMemorySize);
      LOGGER.info(
          "Warning: Heap limit is not set. Using an assumption of 25% of "
              + "total RAM :{} Bytes for safety, for RocksDB memory calculation."
              + "Consider setting -XX:MaxRAMPercentage explicitly.",
          maxHeapBytes);
    }

    // Get Maximum Direct Memory Size, -XX:MaxDirectMemorySize
    // if not set, JVM sets the limit to be equal to the Maximum Heap Size
    final var maxDirectMemoryBytes =
        getMaxDirectMemoryBytes() != 0 ? getMaxDirectMemoryBytes() : maxHeapBytes;

    // Estimate Total JVM Memory Usage (Heap + Non-Heap + Direct Memory)
    final long maxJvmInternalBytes = maxHeapBytes + maxNonHeapBytes + maxDirectMemoryBytes;

    final long availableOffHeapBudget = totalMemorySize - maxJvmInternalBytes;

    // We set the cache capacity to a percentage of the remaining budget and
    // reserve space for native overheads.
    return (long) (availableOffHeapBudget * (1.0 - ROCKSDB_OVERHEAD_FACTOR));
  }

  private static long getMaxDirectMemoryBytes() {
    try {
      final HotSpotDiagnosticMXBean hotSpotDiagnosticMXBean =
          ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
      // This gets the value of the -XX:MaxDirectMemorySize flag
      // returns 0 if the flag is not set
      final String value = hotSpotDiagnosticMXBean.getVMOption("MaxDirectMemorySize").getValue();
      return Long.parseLong(value);
    } catch (final Exception e) {
      // If the MXBean is missing or the VM option name changed, we fall back to returning 0.
      return 0;
    }
  }

  public static void validateRocksDbMemory(final RocksdbCfg rocksdbCfg, final int partitionsCount) {
    final long blockCacheBytes = getBlockCacheBytes(rocksdbCfg, partitionsCount);

    if (blockCacheBytes / partitionsCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsCount));
    }
  }
}
