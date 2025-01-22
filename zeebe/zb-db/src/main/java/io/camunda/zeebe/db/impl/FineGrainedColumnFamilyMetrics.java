/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Arrays;

public final class FineGrainedColumnFamilyMetrics implements ColumnFamilyMetrics {
  private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry;

  private static final Duration[] LATENCY_DURATION_BUCKETS =
      Arrays.stream(exponentialBuckets(0.00001, 2, 15))
          .mapToObj(Duration::ofMillis)
          .toArray(Duration[]::new);

  private final Timer getLatency;
  private final Timer putLatency;
  private final Timer deleteLatency;
  private final Timer iterateLatency;

  public <ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
      FineGrainedColumnFamilyMetrics(final int partitionId, final ColumnFamilyNames columnFamily) {
    final var partitionLabel = String.valueOf(partitionId);
    final var columnFamilyLabel = columnFamily.name();
    getLatency = createTimer(METER_REGISTRY, "get", partitionLabel, columnFamilyLabel);
    putLatency = createTimer(METER_REGISTRY, "put", partitionLabel, columnFamilyLabel);
    deleteLatency = createTimer(METER_REGISTRY, "delete", partitionLabel, columnFamilyLabel);
    iterateLatency = createTimer(METER_REGISTRY, "iterate", partitionLabel, columnFamilyLabel);
  }

  private Timer createTimer(
      final MeterRegistry registry,
      final String operation,
      final String partition,
      final String columnFamily) {
    return io.micrometer.core.instrument.Timer.builder("zeebe.rocksdb.latency")
        .description("Latency of RocksDB operations per column family")
        .tags("partition", partition, "columnFamily", columnFamily, "operation", operation)
        .publishPercentileHistogram() // Enables histogram generation
        .serviceLevelObjectives(LATENCY_DURATION_BUCKETS)
        .register(registry);
  }

  private static long[] exponentialBuckets(final double start, final int factor, final int count) {
    final long[] buckets = new long[count];
    for (int i = 0; i < count; i++) {
      buckets[i] = (long) (start * Math.pow(factor, i));
    }
    return buckets;
  }

  @Override
  public Timer measureGetLatency() {
    return getLatency;
  }

  @Override
  public Timer measurePutLatency() {
    return putLatency;
  }

  @Override
  public Timer measureDeleteLatency() {
    return deleteLatency;
  }

  @Override
  public Timer measureIterateLatency() {
    return iterateLatency;
  }
}
