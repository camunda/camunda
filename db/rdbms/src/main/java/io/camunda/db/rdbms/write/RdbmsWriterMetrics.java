/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.write.queue.ContextType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;
import java.util.function.Supplier;

public class RdbmsWriterMetrics {

  private static final String NAMESPACE = "zeebe.rdbms.exporter";

  private final MeterRegistry meterRegistry;
  private final Timer flushLatency;
  private Sample flushLatencyMeasurement;

  public RdbmsWriterMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
            .publishPercentileHistogram()
            .register(meterRegistry);
  }

  public ResourceSample measureFlushDuration() {
    return Timer.resource(meterRegistry, meterName("flush.duration.seconds"))
        .description("Flush duration of bulk exporters in seconds")
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public void recordBulkSize(final int bulkSize) {
    DistributionSummary.builder(meterName("bulk.size"))
        .description("Exporter bulk size")
        .serviceLevelObjectives(1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000)
        .register(meterRegistry)
        .record(bulkSize);
  }

  public void registerCleanupBackoffDurationGauge(
      final Integer partitionId, final Supplier<Number> supplier) {
    Gauge.builder(meterName("historyCleanup.backoffDuration"), supplier)
        .description("Current backoff duration for cleanup of entity")
        .tag("partitionId", String.valueOf(partitionId))
        .register(meterRegistry);
  }

  public ResourceSample measureHistoryCleanupDuration() {
    return Timer.resource(meterRegistry, meterName("historyCleanup.duration.seconds"))
        .description("History cleanup duration of bulk exporters in seconds")
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public ResourceSample measureUsageMetricsHistoryCleanupDuration() {
    return Timer.resource(meterRegistry, meterName("historyCleanup.usageMetrics.duration.seconds"))
        .description("Usage metrics history cleanup duration in seconds")
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public void recordHistoryCleanupBulkSize(final int bulkSize, final String entityName) {
    DistributionSummary.builder(meterName("historyCleanup.bulk.size"))
        .description("Exporter bulk size")
        .tag("entity", entityName)
        .serviceLevelObjectives(
            1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000,
            100_000)
        .register(meterRegistry)
        .record(bulkSize);
  }

  public void recordFailedFlush() {
    Counter.builder(meterName("failed.flush"))
        .description("Number of failed flush operations")
        .register(meterRegistry)
        .increment();
  }

  public void recordMergedQueueItem(final ContextType contextType, final String statementId) {
    Counter.builder(meterName("merged.queue.item"))
        .tags(
            "contextType", contextType.name(), "statementId", stripDefaultPackageName(statementId))
        .description("Queue item merged into another item")
        .register(meterRegistry)
        .increment();
  }

  public void recordEnqueuedStatement(final String statementId) {
    Counter.builder(meterName("enqueued.statements"))
        .tags("statementId", stripDefaultPackageName(statementId))
        .description("Number of enqueued statements")
        .register(meterRegistry)
        .increment();
  }

  public void recordExecutedStatement(final String statementId, final int batchCount) {
    Counter.builder(meterName("executed.statements"))
        .tags("statementId", stripDefaultPackageName(statementId))
        .description("Number of executed statements")
        .register(meterRegistry)
        .increment();

    Counter.builder(meterName("executed.queue.item"))
        .tags("statementId", stripDefaultPackageName(statementId))
        .description("Number of executed queue items")
        .register(meterRegistry)
        .increment(batchCount);

    DistributionSummary.builder(meterName("num.batches"))
        .tags("statementId", stripDefaultPackageName(statementId))
        .description("Exporter batch count")
        .maximumExpectedValue(100.0)
        .scale(100)
        .serviceLevelObjectives(10, 50, 75, 85, 90, 95, 97, 98, 99, 100)
        .register(meterRegistry)
        .record(1.0 - 1.0 / batchCount);
  }

  public void startFlushLatencyMeasurement() {
    flushLatencyMeasurement = Timer.start(meterRegistry);
  }

  public void stopFlushLatencyMeasurement() {
    if (flushLatencyMeasurement != null) {
      flushLatencyMeasurement.stop(flushLatency);
    }
  }

  private String meterName(final String name) {
    return NAMESPACE + "." + name;
  }

  private static String stripDefaultPackageName(final String statementId) {
    if (statementId.startsWith("io.camunda.db.rdbms.sql")) {
      return statementId.substring("io.camunda.db.rdbms.sql.".length());
    }
    return statementId;
  }
}
