/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

public final class AppenderMetrics {
  private static final Counter TOTAL_DEFERRED_APPEND_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("deferred_append_count_total")
          .help("Number of deferred appends due to backpressure")
          .labelNames("partition")
          .register();

  private static final Counter TOTAL_APPEND_TRY_COUNT =
      Counter.build()
          .namespace("zeebe")
          .name("try_to_append_total")
          .help("Number of tries to append")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_INFLIGHT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_inflight_append_count")
          .help("Current number of append inflight")
          .labelNames("partition")
          .register();

  private static final Gauge CURRENT_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_append_limit")
          .help("Current limit for number of inflight appends")
          .labelNames("partition")
          .register();

  private static final Gauge LAST_COMMITTED_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("log_appender_last_committed_position")
          .help("The last committed position.")
          .labelNames("partition")
          .register();

  private static final Gauge LAST_WRITTEN_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("log_appender_last_appended_position")
          .help("The last appended position by the appender.")
          .labelNames("partition")
          .register();

  private static final Histogram WRITE_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("log_appender_append_latency")
          .help("Latency to append an event to the log in seconds")
          .labelNames("partition")
          .register();
  private static final Histogram COMMIT_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("log_appender_commit_latency")
          .help("Latency to commit an event to the log in seconds")
          .labelNames("partition")
          .register();

  private static final Counter RECORD_APPENDED =
      Counter.build()
          .namespace("zeebe")
          .subsystem("log_appender")
          .name("record_appended")
          .labelNames("partition", "recordType", "valueType", "intent")
          .help("Count of records appended per partition, record type, value type, and intent")
          .register();

  private final Counter.Child deferredAppends;
  private final Counter.Child triedAppends;
  private final Gauge.Child inflightAppends;
  private final Gauge.Child inflightLimit;
  private final Gauge.Child lastCommitted;
  private final Gauge.Child lastWritten;
  private final Histogram.Child commitLatency;
  private final Histogram.Child appendLatency;
  private final String partitionLabel;

  public AppenderMetrics(final int partitionId) {
    partitionLabel = String.valueOf(partitionId);
    deferredAppends = TOTAL_DEFERRED_APPEND_COUNT.labels(partitionLabel);
    triedAppends = TOTAL_APPEND_TRY_COUNT.labels(partitionLabel);
    inflightAppends = CURRENT_INFLIGHT.labels(partitionLabel);
    inflightLimit = CURRENT_LIMIT.labels(partitionLabel);
    lastCommitted = LAST_COMMITTED_POSITION.labels(partitionLabel);
    lastWritten = LAST_WRITTEN_POSITION.labels(partitionLabel);
    commitLatency = COMMIT_LATENCY.labels(partitionLabel);
    appendLatency = WRITE_LATENCY.labels(partitionLabel);
  }

  public void increaseInflight() {
    inflightAppends.inc();
  }

  public void decreaseInflight() {
    inflightAppends.dec();
  }

  public void setInflightLimit(final long limit) {
    inflightLimit.set(limit);
  }

  public void increaseTriedAppends() {
    triedAppends.inc();
  }

  public void increaseDeferredAppends() {
    deferredAppends.inc();
  }

  public Timer startWriteTimer() {
    return appendLatency.startTimer();
  }

  public Timer startCommitTimer() {
    return commitLatency.startTimer();
  }

  public void setLastWrittenPosition(final long position) {
    lastWritten.set(position);
  }

  public void setLastCommittedPosition(final long position) {
    lastCommitted.set(position);
  }

  public void recordAppendedEntry(
      final int amount,
      final RecordType recordType,
      final ValueType valueType,
      final Intent intent) {
    RECORD_APPENDED
        .labels(partitionLabel, recordType.name(), valueType.name(), intent.name())
        .inc(amount);
  }
}
