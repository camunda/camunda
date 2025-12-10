/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

public class SharedResourcesTestHelper implements AutoCloseable {
  public static final long DEFAULT_CACHE_SIZE = 64L * 1024 * 1024;
  public static final long DEFAULT_WRITE_BUFFER_SIZE = DEFAULT_CACHE_SIZE / 4;

  static {
    RocksDB.loadLibrary();
  }

  private SharedRocksDbResources sharedRocksDbResources;

  public SharedRocksDbResources sharedResources() {
    final LRUCache sharedCache = new LRUCache(DEFAULT_CACHE_SIZE, 8, false, 0.15);
    final WriteBufferManager sharedWbm =
        new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, sharedCache);
    sharedRocksDbResources = new SharedRocksDbResources(sharedCache, sharedWbm, DEFAULT_CACHE_SIZE);
    return sharedRocksDbResources;
  }

  @Override
  public void close() {
    sharedRocksDbResources.close();
  }
}
