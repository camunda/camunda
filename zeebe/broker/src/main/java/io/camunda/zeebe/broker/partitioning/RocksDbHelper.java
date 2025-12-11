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
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import java.lang.management.ManagementFactory;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDbHelper {
  public static final long MINIMUM_PARTITION_MEMORY_LIMIT = 32 * 1024 * 1024L;
  private static final double MAX_ROCKSDB_MEMORY_FRACTION = 0.5;

  private static final Logger LOGGER = LoggerFactory.getLogger(RocksDbHelper.class);

  static {
    RocksDB.loadLibrary();
  }

  static SharedRocksDbResources allocateSharedCache(final BrokerCfg brokerCfg) {
    final int partitionsCount = brokerCfg.getCluster().getPartitionsCount();

    final long blockCacheBytes =
        getBlockCacheBytes(brokerCfg.getExperimental().getRocksdb(), partitionsCount);

    LOGGER.debug(
        "Allocating {} bytes for RocksDB, with memory allocation strategy: {}",
        blockCacheBytes,
        brokerCfg.getExperimental().getRocksdb().getMemoryAllocationStrategy());

    // (#DBs) × write_buffer_size × max_write_buffer_number should be comfortably ≤ your WBM limit,
    // with headroom for memtable bloom/filter overhead. write_buffer_size is calculated in
    // zeebeRocksDBFactory.
    final long wbmLimitBytes = blockCacheBytes / 4;
    final LRUCache sharedCache = new LRUCache(blockCacheBytes, 8, false, 0.15);
    final WriteBufferManager sharedWbm = new WriteBufferManager(wbmLimitBytes, sharedCache);
    return new SharedRocksDbResources(sharedCache, sharedWbm, blockCacheBytes);
  }

  public static long getBlockCacheBytes(final RocksdbCfg rocksdbCfg, final int partitionsCount) {
    return switch (rocksdbCfg.getMemoryAllocationStrategy()) {
      case PARTITION -> rocksdbCfg.getMemoryLimit().toBytes() * partitionsCount;
      case BROKER -> rocksdbCfg.getMemoryLimit().toBytes();
      case null ->
          // default to PARTITION strategy for backward compatibility
          rocksdbCfg.getMemoryLimit().toBytes() * partitionsCount;
    };
  }

  public static void validateRocksDbMemory(final RocksdbCfg rocksdbCfg, final int partitionsCount) {
    final long blockCacheBytes = getBlockCacheBytes(rocksdbCfg, partitionsCount);
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    // Heap by default is 25% of the RAM, and off-heap (unless configured otherwise) is the same.
    // So 50% of your RAM goes to the JVM, for both heap and off-heap (aka direct) memory.
    // Leaving 50% of RAM for OS page cache and other processes.

    final long maxRocksDbMem = (long) (totalMemorySize * MAX_ROCKSDB_MEMORY_FRACTION);

    if (blockCacheBytes > maxRocksDbMem) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB to be below or "
                  + "equal %.2f %% of ram memory, but was %.2f %%.",
              MAX_ROCKSDB_MEMORY_FRACTION * 100,
              ((double) blockCacheBytes / totalMemorySize * 100)));
    }

    if (blockCacheBytes / partitionsCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsCount));
    }
  }
}
