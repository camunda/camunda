/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/** Documents all metrics of the RDBMS writer, see {@link RdbmsWriterMetrics}. */
public enum RdbmsWriterMetricsDoc implements ExtendedMeterDocumentation {
  FLUSH_LATENCY(
      "flush.latency",
      Type.TIMER,
      "Time of how long a export buffer is open and collects new records before flushing, meaning latency until the next flush is done."),

  RECORD_EXPORTING_LATENCY(
      "record.exporting.latency",
      Type.TIMER,
      "Time from record creation to commit/flush to the database (end-to-end export latency)"),

  QUEUE_MEMORY_BYTES(
      "queue.memory.bytes",
      Type.DISTRIBUTION_SUMMARY,
      "Estimated memory usage of the execution queue in bytes",
      new double[] {
        1024, // 1KB
        10240, // 10KB
        102400, // 100KB
        1048576, // 1MB
        5242880, // 5MB
        10485760, // 10MB
        52428800, // 50MB
        104857600 // 100MB
      }),

  REPLICATION_CONNECTED_REPLICAS(
      "replication.connected.replicas", Type.GAUGE, "Number of currently connected read replicas"),

  REPLICATION_PAUSED(
      "replication.paused",
      Type.GAUGE,
      "Whether the exporter is currently paused due to replication lag (1 = paused, 0 = running)"),

  REPLICATION_PENDING_POSITIONS(
      "replication.pending.positions",
      Type.GAUGE,
      "Number of exporter positions that have been flushed but not yet confirmed as replicated"),

  REPLICATION_LAG_MS(
      "replication.lag.ms",
      Type.GAUGE,
      "Currently observed replication lag per read replica in milliseconds as reported by the database",
      RdbmsWriterKeyNames.REPLICA_ID),

  FLUSH_DURATION(
      "flush.duration.seconds", Type.TIMER, "Flush duration of bulk exporters in seconds"),

  BULK_SIZE(
      "bulk.size",
      Type.DISTRIBUTION_SUMMARY,
      "Exporter bulk size",
      new double[] {1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000}),

  FAILED_FLUSH("failed.flush", Type.COUNTER, "Number of failed flush operations"),

  FLUSH("flush", Type.COUNTER, "Count of queue flushes", RdbmsWriterKeyNames.TRIGGER),

  MERGED_QUEUE_ITEM(
      "merged.queue.item",
      Type.COUNTER,
      "Queue item merged into another item",
      RdbmsWriterKeyNames.CONTEXT_TYPE,
      RdbmsWriterKeyNames.STATEMENT_ID),

  ENQUEUED_STATEMENTS(
      "enqueued.statements",
      Type.COUNTER,
      "Number of enqueued statements",
      RdbmsWriterKeyNames.STATEMENT_ID),

  EXECUTED_STATEMENTS(
      "executed.statements",
      Type.COUNTER,
      "Number of executed statements",
      RdbmsWriterKeyNames.STATEMENT_ID),

  EXECUTED_QUEUE_ITEM(
      "executed.queue.item",
      Type.COUNTER,
      "Number of executed queue items",
      RdbmsWriterKeyNames.STATEMENT_ID),

  NUM_BATCHES(
      "num.batches",
      Type.DISTRIBUTION_SUMMARY,
      "Exporter batch count",
      new double[] {10, 50, 75, 85, 90, 95, 97, 98, 99, 100},
      RdbmsWriterKeyNames.STATEMENT_ID),

  HISTORY_CLEANUP_BACKOFF_DURATION(
      "historyCleanup.backoffDuration",
      Type.GAUGE,
      "Current backoff duration for cleanup of entity"),

  HISTORY_CLEANUP_DURATION(
      "historyCleanup.duration.seconds",
      Type.TIMER,
      "History cleanup duration of bulk exporters in seconds"),

  HISTORY_CLEANUP_USAGE_METRICS_DURATION(
      "historyCleanup.usageMetrics.duration.seconds",
      Type.TIMER,
      "Usage metrics history cleanup duration in seconds"),

  HISTORY_CLEANUP_JOB_BATCH_METRICS_DURATION(
      "historyCleanup.jobBatchMetrics.duration.seconds",
      Type.TIMER,
      "Job batch metrics history cleanup duration in seconds"),

  HISTORY_CLEANUP_BULK_SIZE(
      "historyCleanup.bulk.size",
      Type.DISTRIBUTION_SUMMARY,
      "Exporter bulk size",
      new double[] {
        1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000
      },
      RdbmsWriterKeyNames.ENTITY),

  HISTORY_CLEANUP_DELETED_ENTITIES(
      "historyCleanup.deletedEntities",
      Type.COUNTER,
      "Number of removed rows of the given entity",
      RdbmsWriterKeyNames.ENTITY);

  private static final String NAMESPACE = "zeebe.rdbms.exporter.";

  private final String name;
  private final Type type;
  private final String description;
  private final double[] distributionSlos;
  private final KeyName[] keyNames;

  RdbmsWriterMetricsDoc(
      final String name, final Type type, final String description, final KeyName... keyNames) {
    this(name, type, description, EMPTY_DISTRIBUTION_SLOS, keyNames);
  }

  RdbmsWriterMetricsDoc(
      final String name,
      final Type type,
      final String description,
      final double[] distributionSlos,
      final KeyName... keyNames) {
    this.name = NAMESPACE + name;
    this.type = type;
    this.description = description;
    this.distributionSlos = distributionSlos;
    this.keyNames = keyNames;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public KeyName[] getKeyNames() {
    return keyNames;
  }

  @Override
  public Duration[] getTimerSLOs() {
    // timers are published as percentile histograms, not with fixed buckets
    return new Duration[0];
  }

  @Override
  public double[] getDistributionSLOs() {
    return distributionSlos;
  }

  public enum RdbmsWriterKeyNames implements KeyName {
    STATEMENT_ID("statementId"),
    CONTEXT_TYPE("contextType"),
    TRIGGER("trigger"),
    ENTITY("entity"),
    REPLICA_ID("replicaId");

    private final String key;

    RdbmsWriterKeyNames(final String key) {
      this.key = key;
    }

    @Override
    public String asString() {
      return key;
    }
  }
}
