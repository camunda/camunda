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
  private final int partitionId;
  private final Timer flushLatency;
  private final Timer recordExportingLatency;
  private final DistributionSummary queueMemoryDistribution;
  private Sample flushLatencyMeasurement;

  public RdbmsWriterMetrics(final MeterRegistry meterRegistry, final int partitionId) {
    this.meterRegistry = meterRegistry;
    this.partitionId = partitionId;

    flushLatency =
        Timer.builder(meterName("flush.latency"))
            .description(
                "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done.")
            .tag("partitionId", String.valueOf(partitionId))
            .publishPercentileHistogram()
            .register(meterRegistry);

    recordExportingLatency =
        Timer.builder(meterName("record.exporting.latency"))
            .description(
                "Time from record creation to commit/flush to the database (end-to-end export latency)")
            .tag("partitionId", String.valueOf(partitionId))
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .register(meterRegistry);

    queueMemoryDistribution =
        DistributionSummary.builder(meterName("queue.memory.bytes"))
            .description("Estimated memory usage of the execution queue in bytes")
            .tag("partitionId", String.valueOf(partitionId))
            .baseUnit("bytes")
            .serviceLevelObjectives(
                1024, // 1KB
                10240, // 10KB
                102400, // 100KB
                1048576, // 1MB
                5242880, // 5MB
                10485760, // 10MB
                52428800, // 50MB
                104857600) // 100MB
            .register(meterRegistry);
  }

  public ResourceSample measureFlushDuration() {
    return Timer.resource(meterRegistry, meterName("flush.duration.seconds"))
        .description("Flush duration of bulk exporters in seconds")
        .tag("partitionId", String.valueOf(partitionId))
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public void recordBulkSize(final int bulkSize) {
    DistributionSummary.builder(meterName("bulk.size"))
        .description("Exporter bulk size")
        .tag("partitionId", String.valueOf(partitionId))
        .serviceLevelObjectives(1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000)
        .register(meterRegistry)
        .record(bulkSize);
  }

  public void registerCleanupBackoffDurationGauge(final Supplier<Number> supplier) {
    Gauge.builder(meterName("historyCleanup.backoffDuration"), supplier)
        .description("Current backoff duration for cleanup of entity")
        .tag("partitionId", String.valueOf(partitionId))
        .register(meterRegistry);
  }

  public ResourceSample measureHistoryCleanupDuration() {
    return Timer.resource(meterRegistry, meterName("historyCleanup.duration.seconds"))
        .description("History cleanup duration of bulk exporters in seconds")
        .tag("partitionId", String.valueOf(partitionId))
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public ResourceSample measureUsageMetricsHistoryCleanupDuration() {
    return Timer.resource(meterRegistry, meterName("historyCleanup.usageMetrics.duration.seconds"))
        .description("Usage metrics history cleanup duration in seconds")
        .tag("partitionId", String.valueOf(partitionId))
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public ResourceSample measureJobBatchMetricsHistoryCleanupDuration() {
    return Timer.resource(
            meterRegistry, meterName("historyCleanup.jobBatchMetrics.duration.seconds"))
        .description("Job batch metrics history cleanup duration in seconds")
        .tag("partitionId", String.valueOf(partitionId))
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
  }

  public void recordHistoryCleanupEntities(final int bulkSize, final String entityName) {
    DistributionSummary.builder(meterName("historyCleanup.bulk.size"))
        .description("Exporter bulk size")
        .tag("partitionId", String.valueOf(partitionId))
        .tag("entity", entityName)
        .serviceLevelObjectives(
            1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000,
            100_000)
        .register(meterRegistry)
        .record(bulkSize);

    Counter.builder(meterName("historyCleanup.deletedEntities"))
        .tags("partitionId", String.valueOf(partitionId), "entity", entityName)
        .description("Number of removed rows of the given entity")
        .register(meterRegistry)
        .increment(bulkSize);
  }

  public void recordFailedFlush() {
    Counter.builder(meterName("failed.flush"))
        .description("Number of failed flush operations")
        .tag("partitionId", String.valueOf(partitionId))
        .register(meterRegistry)
        .increment();
  }

  public void recordMergedQueueItem(final ContextType contextType, final String statementId) {
    Counter.builder(meterName("merged.queue.item"))
        .tags(
            "partitionId",
            String.valueOf(partitionId),
            "contextType",
            contextType.name(),
            "statementId",
            stripDefaultPackageName(statementId))
        .description("Queue item merged into another item")
        .register(meterRegistry)
        .increment();
  }

  public void recordEnqueuedStatement(final String statementId) {
    Counter.builder(meterName("enqueued.statements"))
        .tags(
            "partitionId",
            String.valueOf(partitionId),
            "statementId",
            stripDefaultPackageName(statementId))
        .description("Number of enqueued statements")
        .register(meterRegistry)
        .increment();
  }

  public void recordExecutedStatement(final String statementId, final int batchCount) {
    Counter.builder(meterName("executed.statements"))
        .tags(
            "partitionId",
            String.valueOf(partitionId),
            "statementId",
            stripDefaultPackageName(statementId))
        .description("Number of executed statements")
        .register(meterRegistry)
        .increment();

    Counter.builder(meterName("executed.queue.item"))
        .tags(
            "partitionId",
            String.valueOf(partitionId),
            "statementId",
            stripDefaultPackageName(statementId))
        .description("Number of executed queue items")
        .register(meterRegistry)
        .increment(batchCount);

    DistributionSummary.builder(meterName("num.batches"))
        .tags(
            "partitionId",
            String.valueOf(partitionId),
            "statementId",
            stripDefaultPackageName(statementId))
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

  public void recordQueueMemoryUsage(final long memoryBytes) {
    queueMemoryDistribution.record(memoryBytes);
  }

  /**
   * Records the exporting latency - the time from when the oldest record in the batch was created
   * to when it was committed/flushed to the database.
   *
   * @param latencyMs the latency in milliseconds
   */
  public void recordExportingLatency(final long latencyMs) {
    recordExportingLatency.record(Duration.ofMillis(latencyMs));
  }

  /**
   * Records which trigger caused the queue flush.
   *
   * @param trigger the flush trigger type (e.g., "count_limit", "memory_limit", "flush_interval")
   */
  public void recordQueueFlush(final FlushTrigger trigger) {
    Counter.builder(meterName("flush"))
        .tags("partitionId", String.valueOf(partitionId), "trigger", trigger.name())
        .description("Count of queue flushes")
        .register(meterRegistry)
        .increment();
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

  public enum FlushTrigger {
    COUNT_LIMIT,
    MEMORY_LIMIT,
    FLUSH_INTERVAL,
    SHUTDOWN
  }
}
