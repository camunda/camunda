/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
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
      // in case of PARTITION or null, we allocate per partition
      default -> rocksdbCfg.getMemoryLimit().toBytes() * partitionsCount;
    };
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
