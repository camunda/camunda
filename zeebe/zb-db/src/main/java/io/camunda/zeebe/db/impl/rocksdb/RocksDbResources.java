/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static io.camunda.zeebe.util.ByteValue.prettyPrint;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import org.jspecify.annotations.NonNull;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the memory resources required by RocksDB, such as block caches and write buffer
 * managers, for different allocation strategies.
 */
public sealed interface RocksDbResources {
  Logger LOG = LoggerFactory.getLogger(RocksDbResources.class);

  static RocksDbResources of(final RocksDbConfiguration config, final RuntimeInfo runtime) {
    return switch (config.getMemoryAllocationStrategy()) {
      case PARTITION -> {
        final var memoryLimit = config.getMemoryLimit();
        LOG.atInfo()
            .addArgument(prettyPrint(memoryLimit))
            .log("RocksDB memory set to {} for each partition");
        yield new PerPartition(memoryLimit);
      }
      case BROKER -> {
        final var memoryLimit = config.getMemoryLimit();
        LOG.atInfo()
            .addArgument(prettyPrint(memoryLimit))
            .addArgument(runtime.partitionCount())
            .log("RocksDB memory set to {} for this broker, split over {} partitions");
        yield new Shared(memoryLimit, runtime.partitionCount());
      }
      case FRACTION -> {
        final var memoryLimit = Math.round(runtime.hostMemory() * config.getMemoryFraction());
        LOG.atInfo()
            .addArgument(config.getMemoryFraction())
            .addArgument(prettyPrint(runtime.hostMemory()))
            .addArgument(prettyPrint(memoryLimit))
            .addArgument(runtime.partitionCount())
            .log("RocksDB memory set to {} of host memory {}, sharing {} between {} partitions");
        yield new Shared(memoryLimit, runtime.partitionCount());
      }
    };
  }

  /** Write buffer budget for a single partition's DB, after splitting any shared budget. */
  long writeBufferBudgetPerPartition();

  record RuntimeInfo(long hostMemory, int partitionCount) {
    public RuntimeInfo(final int partitionCount) {
      this(
          ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
              .getTotalMemorySize(),
          partitionCount);
    }
  }

  /**
   * All partitions share a single block cache and write buffer manager. These need to be closed
   * when the broker is stopped.
   */
  final class Shared implements RocksDbResources {
    static {
      RocksDB.loadLibrary();
    }

    private final LRUCache cache;
    private final WriteBufferManager writeBufferManager;
    private final long writeBufferBudget;
    private final int partitionCount;

    public Shared(final long memoryLimit, final int partitionCount) {
      cache = new LRUCache(memoryLimit, 8, false, 0.15);
      // Use up to 2/3 of the memory budget for write buffers
      writeBufferBudget = (long) (memoryLimit * (2 / 3.0));
      writeBufferManager = new WriteBufferManager(writeBufferBudget, cache);
      this.partitionCount = partitionCount;
    }

    @Override
    public long writeBufferBudgetPerPartition() {
      return writeBufferBudget / partitionCount;
    }

    /**
     * Returns the shared block cache instance. Must be closed when the broker is stopped or when
     * all partitions are closed.
     */
    public @NonNull LRUCache getSharedCache() {
      return cache;
    }

    /**
     * Returns the shared write buffer manager instance. Must be closed when the broker is stopped
     * or when all partitions are closed.
     */
    public @NonNull WriteBufferManager getSharedWriteBufferManager() {
      return writeBufferManager;
    }
  }

  /**
   * Each partition has its own block cache that needs to be closed when the partition is stopped.
   */
  final class PerPartition implements RocksDbResources {
    static {
      RocksDB.loadLibrary();
    }

    private final long memoryLimit;
    private final long blockCacheBudget;

    public PerPartition(final long memoryLimit) {
      this.memoryLimit = memoryLimit;
      blockCacheBudget = memoryLimit / 3;
    }

    /**
     * Creates a new block cache instance for a partition. The caller is responsible for closing.
     */
    public @NonNull LRUCache createNewCache() {
      return new LRUCache(blockCacheBudget, 8, false, 0.15);
    }

    @Override
    public long writeBufferBudgetPerPartition() {
      return memoryLimit - blockCacheBudget;
    }
  }
}
