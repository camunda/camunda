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

public final class FineGrainedColumnFamilyMetrics implements ColumnFamilyMetrics {
  private static final MeterRegistry METER_REGISTRY = Metrics.globalRegistry;
  private static final String NAMESPACE = "zeebe";
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

  private static Duration[] generateExponentialBuckets(
      final double start, final int factor, final int count) {
    final Duration[] buckets = new Duration[count];
    for (int i = 0; i < count; i++) {
      buckets[i] = Duration.ofNanos((long) (start * Math.pow(factor, i) * 1_000_000_000));
    }
    return buckets;
  }

  private Timer createTimer(
      final MeterRegistry registry,
      final String operation,
      final String partition,
      final String columnFamily) {
    return Timer.builder(NAMESPACE + "_rocksdb_latency")
        .description("Latency of RocksDB operations per column family")
        .tags("partition", partition, "columnFamily", columnFamily, "operation", operation)
        .publishPercentileHistogram() // Enables histogram generation
        .serviceLevelObjectives(generateExponentialBuckets(0.00001, 2, 15))
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
  public TimerContext measureGetLatency() {
    return new TimerContext(getLatency);
  }

  @Override
  public TimerContext measurePutLatency() {
    return new TimerContext(putLatency);
  }

  @Override
  public TimerContext measureDeleteLatency() {
    return new TimerContext(deleteLatency);
  }

  @Override
  public TimerContext measureIterateLatency() {
    return new TimerContext(iterateLatency);
  }

  /** A helper class for handling AutoCloseable Timer measurement. */
  public static class TimerContext implements AutoCloseable {
    private final Timer timer;
    private final Timer.Sample sample;

    public TimerContext(final Timer timer) {
      this.timer = timer;
      sample = Timer.start();
    }

    @Override
    public void close() {
      sample.stop(timer);
    }
  }
}
