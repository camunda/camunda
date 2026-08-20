/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

public final class RocksDbSharedCacheMetrics {

  public static void registerAllocationStrategy(
      final MeterRegistry registry, final MemoryAllocationStrategy strategy) {

    final var meterDoc = RocksDbSharedCacheMetricsDoc.MEMORY_ALLOCATION_STRATEGY;
    Gauge.builder(meterDoc.getName(), () -> 1)
        .description(meterDoc.getDescription())
        .tag(RocksDbSharedCacheMetricsDoc.RocksDbKeyNames.STRATEGY.asString(), strategy.name())
        .register(registry);
  }

  @SuppressWarnings("NullableProblems")
  public enum RocksDbSharedCacheMetricsDoc implements ExtendedMeterDocumentation {
    MEMORY_ALLOCATION_STRATEGY {
      @Override
      public String getDescription() {
        return "The memory allocation strategy used for RocksDB";
      }

      @Override
      public String getName() {
        return "zeebe.rocksdb.memory.allocation.strategy";
      }

      @Override
      public Meter.Type getType() {
        return Meter.Type.GAUGE;
      }

      @Override
      public KeyName[] getKeyNames() {
        return RocksDbKeyNames.values();
      }
    };

    enum RocksDbKeyNames implements KeyName {
      STRATEGY {
        @Override
        public String asString() {
          return "strategy";
        }
      }
    }
  }
}
