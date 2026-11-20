/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.test;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/**
 * Test database factory for the broker's column families with a small memory limit per partition.
 */
public final class TestZeebeDbFactory {

  private static final long MEMORY_LIMIT_BYTES = 32 * 1024 * 1024;

  private TestZeebeDbFactory() {}

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    return DefaultZeebeDbFactory.getDefaultFactory(
        MemoryAllocationStrategy.PARTITION, MEMORY_LIMIT_BYTES);
  }
}
