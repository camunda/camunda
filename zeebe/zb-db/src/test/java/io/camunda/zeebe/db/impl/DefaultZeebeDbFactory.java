/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

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

public final class DefaultZeebeDbFactory {

  public static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory() {
    return getDefaultFactory(SimpleMeterRegistry::new);
  }

  public static <
          ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory(
          final Supplier<MeterRegistry> meterRegistry) {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        consistencyChecks,
        new AccessMetricsConfiguration(Kind.NONE, 1),
        meterRegistry);
  }
}
