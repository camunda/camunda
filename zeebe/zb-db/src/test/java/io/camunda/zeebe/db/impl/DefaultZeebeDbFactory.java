/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Supplier;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

public final class DefaultZeebeDbFactory {

  static {
    RocksDB.loadLibrary();
  }

  public static <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactoryResources<T> getDefaultFactoryResources() {
    return getDefaultFactoryResources(SimpleMeterRegistry::new);
  }

  public static <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactoryResources<T> getDefaultFactoryResources(
          final Supplier<MeterRegistry> meterRegistry) {
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    final WriteBufferManager writeBufferManager =
        new WriteBufferManager(ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE, lruCache);
    final int defaultPartitionCount = 3;
    final ZeebeDbFactory<T> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            consistencyChecks,
            new AccessMetricsConfiguration(Kind.NONE, 1),
            meterRegistry,
            lruCache,
            writeBufferManager,
            defaultPartitionCount);
    return new ZeebeDbFactoryResources<>(factory, lruCache, writeBufferManager);
  }

  public static class ZeebeDbFactoryResources<
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      implements AutoCloseable {
    public final ZeebeDbFactory<ColumnFamilyType> factory;
    private final LRUCache lruCache;
    private final WriteBufferManager writeBufferManager;

    public ZeebeDbFactoryResources(
        final ZeebeDbFactory<ColumnFamilyType> factory,
        final LRUCache lruCache,
        final WriteBufferManager writeBufferManager) {
      this.factory = factory;
      this.lruCache = lruCache;
      this.writeBufferManager = writeBufferManager;
    }

    @Override
    public void close() {
      writeBufferManager.close();
      lruCache.close();
    }
  }
}
