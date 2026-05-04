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
import org.rocksdb.WriteBufferManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public sealed interface RocksDbMemory {
  Logger LOG = LoggerFactory.getLogger(RocksDbMemory.class);

  static RocksDbMemory of(final RocksDbConfiguration config, final RuntimeInfo runtime) {
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
        final var memoryLimitPerPartition = memoryLimit / runtime.partitionCount();
        LOG.atInfo()
            .addArgument(prettyPrint(memoryLimit))
            .addArgument(runtime.partitionCount())
            .log("RocksDB memory set to {} for this broker, split over {} partitions");
        yield new PerPartition(memoryLimitPerPartition);
      }
      case FRACTION -> {
        final var memoryLimit = Math.round(runtime.hostMemory() * config.getMemoryFraction());
        LOG.atInfo()
            .addArgument(config.getMemoryFraction())
            .addArgument(prettyPrint(runtime.hostMemory()))
            .addArgument(prettyPrint(memoryLimit))
            .addArgument(runtime.partitionCount())
            .log("RocksDB memory set to {} of host memory {}, sharing {} between {} partitions");
        yield new Shared(memoryLimit);
      }
    };
  }

  record RuntimeInfo(long hostMemory, int partitionCount) {
    public RuntimeInfo(final int partitionCount) {
      this(
          ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
              .getTotalMemorySize(),
          partitionCount);
    }
  }

  final class Shared implements RocksDbMemory {
    private final LRUCache cache;
    private final WriteBufferManager writeBufferManager;
    private final long writeBufferBudget;

    public Shared(final long memoryLimit) {
      cache = new LRUCache(memoryLimit, 8, false, 0.15);
      writeBufferBudget = (long) (memoryLimit * (2 / 3.0));
      writeBufferManager = new WriteBufferManager(writeBufferBudget, cache);
    }

    public long getWriteBufferBudget() {
      return writeBufferBudget;
    }

    public @NonNull LRUCache getSharedCache() {
      return cache;
    }

    public @NonNull WriteBufferManager getSharedWriteBufferManager() {
      return writeBufferManager;
    }
  }

  final class PerPartition implements RocksDbMemory {
    private final long memoryLimit;
    private final long blockCacheBudget;

    public PerPartition(final long memoryLimit) {
      this.memoryLimit = memoryLimit;
      blockCacheBudget = memoryLimit / 3;
    }

    public @NonNull LRUCache createNewCache() {
      return new LRUCache(memoryLimit / 3, 8, false, 0.15);
    }

    public long getWriteBufferBudget() {
      return memoryLimit - blockCacheBudget;
    }
  }
}
