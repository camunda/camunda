/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import static io.camunda.db.rdbms.write.RdbmsWriterMetricsDoc.RdbmsWriterKeyNames.CONTEXT_TYPE;
import static io.camunda.db.rdbms.write.RdbmsWriterMetricsDoc.RdbmsWriterKeyNames.ENTITY;
import static io.camunda.db.rdbms.write.RdbmsWriterMetricsDoc.RdbmsWriterKeyNames.REPLICA_ID;
import static io.camunda.db.rdbms.write.RdbmsWriterMetricsDoc.RdbmsWriterKeyNames.STATEMENT_ID;
import static io.camunda.db.rdbms.write.RdbmsWriterMetricsDoc.RdbmsWriterKeyNames.TRIGGER;

import io.camunda.db.rdbms.read.replication.ReplicationStatus;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.ResourceSample;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RdbmsWriterMetrics {

  private final MeterRegistry meterRegistry;
  private final Timer flushLatency;
  private final Timer recordExportingLatency;
  private final DistributionSummary queueMemoryDistribution;
  private Sample flushLatencyMeasurement;

  private final AtomicInteger connectedReplicasValue = new AtomicInteger(0);
  private final AtomicInteger exporterPausedValue = new AtomicInteger(0);
  private final AtomicLong pendingPositionsValue = new AtomicLong(0);
  private final Map<String, StatefulGauge> replicaLagGauges = new HashMap<>();

  public RdbmsWriterMetrics(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;

    flushLatency =
        Timer.builder(RdbmsWriterMetricsDoc.FLUSH_LATENCY.getName())
            .description(RdbmsWriterMetricsDoc.FLUSH_LATENCY.getDescription())
            .publishPercentileHistogram()
            .register(meterRegistry);

    recordExportingLatency =
        Timer.builder(RdbmsWriterMetricsDoc.RECORD_EXPORTING_LATENCY.getName())
            .description(RdbmsWriterMetricsDoc.RECORD_EXPORTING_LATENCY.getDescription())
            .publishPercentileHistogram()
            .minimumExpectedValue(Duration.ofMillis(1))
            .register(meterRegistry);

    queueMemoryDistribution =
        MicrometerUtil.buildSummary(RdbmsWriterMetricsDoc.QUEUE_MEMORY_BYTES)
            .baseUnit("bytes")
            .register(meterRegistry);

    Gauge.builder(
            RdbmsWriterMetricsDoc.REPLICATION_CONNECTED_REPLICAS.getName(),
            connectedReplicasValue,
            AtomicInteger::get)
        .description(RdbmsWriterMetricsDoc.REPLICATION_CONNECTED_REPLICAS.getDescription())
        .register(meterRegistry);

    Gauge.builder(
            RdbmsWriterMetricsDoc.REPLICATION_PAUSED.getName(),
            exporterPausedValue,
            AtomicInteger::get)
        .description(RdbmsWriterMetricsDoc.REPLICATION_PAUSED.getDescription())
        .register(meterRegistry);

    Gauge.builder(
            RdbmsWriterMetricsDoc.REPLICATION_PENDING_POSITIONS.getName(),
            pendingPositionsValue,
            AtomicLong::get)
        .description(RdbmsWriterMetricsDoc.REPLICATION_PENDING_POSITIONS.getDescription())
        .register(meterRegistry);
  }

  public ResourceSample measureFlushDuration() {
    return durationResource(RdbmsWriterMetricsDoc.FLUSH_DURATION);
  }

  public void recordBulkSize(final int bulkSize) {
    MicrometerUtil.buildSummary(RdbmsWriterMetricsDoc.BULK_SIZE)
        .register(meterRegistry)
        .record(bulkSize);
  }

  public void registerCleanupBackoffDurationGauge(final Supplier<Number> supplier) {
    Gauge.builder(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_BACKOFF_DURATION.getName(), supplier)
        .description(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_BACKOFF_DURATION.getDescription())
        .register(meterRegistry);
  }

  public ResourceSample measureHistoryCleanupDuration() {
    return durationResource(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_DURATION);
  }

  public ResourceSample measureUsageMetricsHistoryCleanupDuration() {
    return durationResource(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_USAGE_METRICS_DURATION);
  }

  public ResourceSample measureJobBatchMetricsHistoryCleanupDuration() {
    return durationResource(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_JOB_BATCH_METRICS_DURATION);
  }

  public void recordHistoryCleanupEntities(final int bulkSize, final String entityName) {
    MicrometerUtil.buildSummary(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_BULK_SIZE)
        .tag(ENTITY.asString(), entityName)
        .register(meterRegistry)
        .record(bulkSize);

    Counter.builder(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_DELETED_ENTITIES.getName())
        .tags(ENTITY.asString(), entityName)
        .description(RdbmsWriterMetricsDoc.HISTORY_CLEANUP_DELETED_ENTITIES.getDescription())
        .register(meterRegistry)
        .increment(bulkSize);
  }

  public void recordFailedFlush() {
    Counter.builder(RdbmsWriterMetricsDoc.FAILED_FLUSH.getName())
        .description(RdbmsWriterMetricsDoc.FAILED_FLUSH.getDescription())
        .register(meterRegistry)
        .increment();
  }

  public void recordMergedQueueItem(final ContextType contextType, final String statementId) {
    Counter.builder(RdbmsWriterMetricsDoc.MERGED_QUEUE_ITEM.getName())
        .tags(
            CONTEXT_TYPE.asString(),
            contextType.name(),
            STATEMENT_ID.asString(),
            stripDefaultPackageName(statementId))
        .description(RdbmsWriterMetricsDoc.MERGED_QUEUE_ITEM.getDescription())
        .register(meterRegistry)
        .increment();
  }

  public void recordEnqueuedStatement(final String statementId) {
    Counter.builder(RdbmsWriterMetricsDoc.ENQUEUED_STATEMENTS.getName())
        .tags(STATEMENT_ID.asString(), stripDefaultPackageName(statementId))
        .description(RdbmsWriterMetricsDoc.ENQUEUED_STATEMENTS.getDescription())
        .register(meterRegistry)
        .increment();
  }

  public void recordExecutedStatement(final String statementId, final int batchCount) {
    Counter.builder(RdbmsWriterMetricsDoc.EXECUTED_STATEMENTS.getName())
        .tags(STATEMENT_ID.asString(), stripDefaultPackageName(statementId))
        .description(RdbmsWriterMetricsDoc.EXECUTED_STATEMENTS.getDescription())
        .register(meterRegistry)
        .increment();

    Counter.builder(RdbmsWriterMetricsDoc.EXECUTED_QUEUE_ITEM.getName())
        .tags(STATEMENT_ID.asString(), stripDefaultPackageName(statementId))
        .description(RdbmsWriterMetricsDoc.EXECUTED_QUEUE_ITEM.getDescription())
        .register(meterRegistry)
        .increment(batchCount);

    MicrometerUtil.buildSummary(RdbmsWriterMetricsDoc.NUM_BATCHES)
        .tags(STATEMENT_ID.asString(), stripDefaultPackageName(statementId))
        .maximumExpectedValue(100.0)
        .scale(100)
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
    Counter.builder(RdbmsWriterMetricsDoc.FLUSH.getName())
        .tags(TRIGGER.asString(), trigger.name())
        .description(RdbmsWriterMetricsDoc.FLUSH.getDescription())
        .register(meterRegistry)
        .increment();
  }

  /**
   * Records the current replication lag per replica, the connected replica count, the paused state,
   * and the number of pending (flushed-but-not-yet-replicated) positions.
   *
   * @param statuses the current replication statuses returned by the database
   * @param paused whether the exporter is currently paused due to replication lag
   * @param flushedPosition the last position flushed to the database by this exporter
   * @param replicatedPosition the last position confirmed as replicated by the required quorum
   */
  public void recordReplicationStatus(
      final List<? extends ReplicationStatus> statuses,
      final boolean paused,
      final long flushedPosition,
      final long replicatedPosition) {
    connectedReplicasValue.set(statuses.size());
    exporterPausedValue.set(paused ? 1 : 0);
    pendingPositionsValue.set(Math.max(0, flushedPosition - replicatedPosition));

    final var currentReplicaIds =
        statuses.stream().map(ReplicationStatus::replicaId).collect(Collectors.toSet());

    final var staleReplicaIds = new ArrayList<>(replicaLagGauges.keySet());
    staleReplicaIds.removeAll(currentReplicaIds);
    staleReplicaIds.forEach(id -> meterRegistry.remove(replicaLagGauges.remove(id)));

    for (final var status : statuses) {
      final var gauge =
          replicaLagGauges.computeIfAbsent(status.replicaId(), this::registerReplicaLagGauge);
      final var lag = status.replicationLagMs();
      gauge.set(lag != null ? lag.doubleValue() : Double.NaN);
    }
  }

  private StatefulGauge registerReplicaLagGauge(final String replicaId) {
    return StatefulGauge.builder(RdbmsWriterMetricsDoc.REPLICATION_LAG_MS.getName())
        .description(RdbmsWriterMetricsDoc.REPLICATION_LAG_MS.getDescription())
        .tag(REPLICA_ID.asString(), replicaId)
        .register(meterRegistry);
  }

  private ResourceSample durationResource(final RdbmsWriterMetricsDoc doc) {
    return Timer.resource(meterRegistry, doc.getName())
        .description(doc.getDescription())
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(10));
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
