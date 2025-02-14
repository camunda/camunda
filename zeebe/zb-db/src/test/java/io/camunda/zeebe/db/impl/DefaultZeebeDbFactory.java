/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.micrometer.StatefulMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class DefaultZeebeDbFactory {

  public static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory() {
    return getDefaultFactory(new SimpleMeterRegistry());
  }

  public static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory(final MeterRegistry meterRegistry) {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(true, true);
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration(),
        consistencyChecks,
        new AccessMetricsConfiguration(Kind.NONE, 1),
        () -> new StatefulMeterRegistry(meterRegistry, Tags.empty()));
  }
}
