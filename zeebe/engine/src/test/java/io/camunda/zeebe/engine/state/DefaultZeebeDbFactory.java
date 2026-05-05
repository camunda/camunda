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
import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class DefaultZeebeDbFactory {

  private static final ConsistencyChecksSettings CONSISTENCY_CHECKS =
      new ConsistencyChecksSettings(true, true);
  private static final AccessMetricsConfiguration ACCESS_METRICS_CONFIGURATION =
      new AccessMetricsConfiguration(Kind.NONE, 1);

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        CONSISTENCY_CHECKS,
        ACCESS_METRICS_CONFIGURATION,
        SimpleMeterRegistry::new);
  }

  public static ZeebeDbFactory<ZbColumnFamilies> inMemoryFactory() {
    return new InMemoryZeebeDbFactory<>(
        CONSISTENCY_CHECKS, ACCESS_METRICS_CONFIGURATION, SimpleMeterRegistry::new);
  }
}
