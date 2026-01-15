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
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDbSharedCache {
  public static final long MINIMUM_PARTITION_MEMORY_LIMIT = 32 * 1024 * 1024L;

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
      case AUTO -> getFixedMemoryPercentage(rocksdbCfg.getMemoryFraction());
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
    // makes sure that memoryFraction is between [0 and 1] when using AUTO strategy
    if (rocksdbCfg.getMemoryAllocationStrategy() == MemoryAllocationStrategy.AUTO
        && (rocksdbCfg.getMemoryFraction() <= 0.0 || rocksdbCfg.getMemoryFraction() >= 1.0)) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the memoryFraction for RocksDB AUTO memory allocation strategy to be between 0 and 1, but was %s.",
              rocksdbCfg.getMemoryFraction()));
    }

    final long blockCacheBytes = getBlockCacheBytes(rocksdbCfg, partitionsCount);

    if (blockCacheBytes / partitionsCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsCount));
    }
  }
}
