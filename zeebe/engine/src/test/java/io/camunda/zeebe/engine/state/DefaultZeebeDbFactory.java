/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

public final class DefaultZeebeDbFactory {

  static {
    RocksDB.loadLibrary();
  }

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    final int defaultPartitionCount = 3;
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        consistencyChecks,
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new,
        lruCache,
        new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, lruCache),
        defaultPartitionCount);
  }
}
