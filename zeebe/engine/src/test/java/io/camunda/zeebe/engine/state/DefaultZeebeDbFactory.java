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
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.sqlite.SqliteDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Test database factory. Defaults to RocksDB; run with {@code -Dzeebe.test.storage=sqlite} to run
 * the tests against the SQLite backend.
 */
public final class DefaultZeebeDbFactory {

  public static final String STORAGE_PROPERTY = "zeebe.test.storage";

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    final var storage = System.getProperty(STORAGE_PROPERTY, "rocksdb");
    return switch (storage) {
      case "rocksdb" ->
          new ZeebeRocksDbFactory<>(
              new RocksDbConfiguration(),
              consistencyChecks,
              new AccessMetricsConfiguration(Kind.NONE),
              SimpleMeterRegistry::new);
      case "sqlite" -> new SqliteDbFactory<>(consistencyChecks, SimpleMeterRegistry::new);
      default ->
          throw new IllegalArgumentException(
              "Unknown -D%s=%s, expected rocksdb or sqlite".formatted(STORAGE_PROPERTY, storage));
    };
  }
}
