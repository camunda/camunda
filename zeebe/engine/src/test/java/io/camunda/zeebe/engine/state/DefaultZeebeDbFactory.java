/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.SharedResourcesTestHelper;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AutoClose;

public final class DefaultZeebeDbFactory {

  public static ZeebeDbFactoryResources getDefaultFactoryResources() {
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final SharedRocksDbResources sharedRocksDbResources =
        new SharedResourcesTestHelper().sharedResources();
    final int defaultPartitionCount = 3;
    final ZeebeDbFactory<ZbColumnFamilies> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            consistencyChecks,
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            sharedRocksDbResources,
            defaultPartitionCount);
    return new ZeebeDbFactoryResources(factory, sharedRocksDbResources);
  }

  public static ZeebeDbFactoryResources getDefaultFactoryResources(final long cacheSize) {
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final SharedRocksDbResources sharedRocksDbResources =
        new SharedResourcesTestHelper().sharedResources(cacheSize);
    final int defaultPartitionCount = 3;
    final ZeebeDbFactory<ZbColumnFamilies> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            consistencyChecks,
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            sharedRocksDbResources,
            defaultPartitionCount);
    return new ZeebeDbFactoryResources(factory, sharedRocksDbResources);
  }

  public static class ZeebeDbFactoryResources implements AutoCloseable {
    public final ZeebeDbFactory<ZbColumnFamilies> factory;
    @AutoClose private final SharedRocksDbResources sharedRocksDbResources;

    public ZeebeDbFactoryResources(
        final ZeebeDbFactory<ZbColumnFamilies> factory,
        final SharedRocksDbResources sharedRocksDbResources) {
      this.factory = factory;
      this.sharedRocksDbResources = sharedRocksDbResources;
    }

    @Override
    public void close() {
      sharedRocksDbResources.close();
    }
  }
}
