/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ElasticsearchMetrics {
  private static final String NAMESPACE = "zeebe.elasticsearch.exporter";

  private final MeterRegistry meterRegistry;
  private final AtomicInteger bulkMemorySize = new AtomicInteger(0);
  private final Timer flushDuration;
  private final DistributionSummary bulkSize;
  private final Counter failedFlush;
  private final Timer flushLatency;

  public ElasticsearchMetrics(final MeterRegistry registry) {
    meterRegistry = registry;

    Gauge.builder(meterName("bulk.memory.size"), bulkMemorySize, AtomicInteger::get)
        .description("Exporter bulk memory size")
        .register(meterRegistry);

    flushDuration =
        Timer.builder(meterName("flush.duration.seconds"))
            .description("Flush duration of bulk exporters in seconds")
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(10))
            .register(meterRegistry);

    bulkSize =
        DistributionSummary.builder(meterName("bulk.size"))
            .description("Exporter bulk size")
            .serviceLevelObjectives(10, 100, 1_000, 10_000, 100_000)
            .register(meterRegistry);

    failedFlush =
        Counter.builder(meterName("failed.flush"))
            .description("Number of failed flush operations")
            .register(meterRegistry);

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  public void measureFlushDuration(final Runnable flushFunction) {
    flushDuration.record(flushFunction);
  }

  public void recordBulkSize(final int bulkSize) {
    this.bulkSize.record(bulkSize);
  }

  public void recordBulkMemorySize(final int bulkMemorySize) {
    this.bulkMemorySize.set(bulkMemorySize);
  }

  public void recordFailedFlush() {
    failedFlush.increment();
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + name;
  }

  public Timer.Sample startFlushLatencyMeasurement() {
    return Timer.start(meterRegistry);
  }

  public void stopFlushLatencyMeasurement(final Timer.Sample flushLatencySample) {
    if (flushLatencySample != null) {
      flushLatencySample.stop(flushLatency);
    }
  }
}
