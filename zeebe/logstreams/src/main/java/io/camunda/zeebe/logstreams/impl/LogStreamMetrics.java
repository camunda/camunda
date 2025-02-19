/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl;

import static io.camunda.zeebe.logstreams.impl.LogStreamMetrics.FlowControlOutComeLabels.labelForContext;
import static io.camunda.zeebe.logstreams.impl.LogStreamMetrics.FlowControlOutComeLabels.labelForReason;

import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl.Rejection;
import io.camunda.zeebe.logstreams.impl.log.LogAppendEntryMetadata;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.log.WriteContext.InterPartition;
import io.camunda.zeebe.logstreams.log.WriteContext.Internal;
import io.camunda.zeebe.logstreams.log.WriteContext.ProcessingResult;
import io.camunda.zeebe.logstreams.log.WriteContext.Scheduled;
import io.camunda.zeebe.logstreams.log.WriteContext.UserCommand;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;
import java.util.EnumSet;
import java.util.List;

public final class LogStreamMetrics {
  private static final Counter FLOW_CONTROL_OUTCOME =
      Counter.build()
          .namespace("zeebe")
          .subsystem("flow_control")
          .name("outcome")
          .help(
              "The count of records passing through the flow control, organized by context and outcome")
          .labelNames("partition", "context", "outcome")
          .register();

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

  private static final Gauge INFLIGHT_APPENDS =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_inflight_append_count")
          .help("Current number of append inflight")
          .labelNames("partition")
          .register();

  private static final Counter TOTAL_RECEIVED_REQUESTS =
      Counter.build()
          .namespace("zeebe")
          .name("received_request_count_total")
          .help("Number of requests received")
          .labelNames("partition")
          .register();

  private static final Counter TOTAL_DROPPED_REQUESTS =
      Counter.build()
          .namespace("zeebe")
          .name("dropped_request_count_total")
          .help("Number of requests dropped due to backpressure")
          .labelNames("partition")
          .register();

  private static final Gauge INFLIGHT_REQUESTS =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_inflight_requests_count")
          .help("Current number of request inflight")
          .labelNames("partition")
          .register();

  private static final Gauge REQUEST_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .name("backpressure_requests_limit")
          .help("Current limit for number of inflight requests")
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

  private static final Gauge EXPORTING_RATE =
      Gauge.build()
          .namespace("zeebe")
          .subsystem("flow_control")
          .name("exporting_rate")
          .help("The rate of exporting records from the log appender")
          .labelNames("partition")
          .register();

  private static final Gauge WRITE_RATE_MAX_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .subsystem("flow_control")
          .name("write_rate_maximum")
          .help("The maximum write rate limit")
          .labelNames("partition")
          .register();

  private static final Gauge WRITE_RATE_LIMIT =
      Gauge.build()
          .namespace("zeebe")
          .subsystem("flow_control")
          .name("write_rate_limit")
          .help("The current write rate limit")
          .labelNames("partition")
          .register();

  private static final Gauge PARTITION_LOAD =
      Gauge.build()
          .namespace("zeebe")
          .subsystem("flow_control")
          .name("partition_load")
          .labelNames("partition")
          .help(
              "The current load of the partition. Determined by observed write rate compared to the write rate limit")
          .register();

  private final Counter.Child deferredAppends;
  private final Counter.Child triedAppends;
  private final Gauge.Child inflightAppends;
  private final Counter.Child receivedRequests;
  private final Counter.Child droppedRequests;
  private final Gauge.Child inflightRequests;
  private final Gauge.Child requestLimit;
  private final Gauge.Child lastCommitted;
  private final Gauge.Child lastWritten;
  private final Histogram.Child commitLatency;
  private final Histogram.Child appendLatency;
  private final Gauge.Child exportingRate;
  private final Gauge.Child writeRateMaxLimit;
  private final Gauge.Child writeRateLimit;
  private final Gauge.Child partitionLoad;
  private final String partitionLabel;

  public LogStreamMetrics(final int partitionId) {
    partitionLabel = String.valueOf(partitionId);
    deferredAppends = TOTAL_DEFERRED_APPEND_COUNT.labels(partitionLabel);
    triedAppends = TOTAL_APPEND_TRY_COUNT.labels(partitionLabel);
    inflightAppends = INFLIGHT_APPENDS.labels(partitionLabel);
    receivedRequests = TOTAL_RECEIVED_REQUESTS.labels(partitionLabel);
    droppedRequests = TOTAL_DROPPED_REQUESTS.labels(partitionLabel);
    inflightRequests = INFLIGHT_REQUESTS.labels(partitionLabel);
    requestLimit = REQUEST_LIMIT.labels(partitionLabel);
    lastCommitted = LAST_COMMITTED_POSITION.labels(partitionLabel);
    lastWritten = LAST_WRITTEN_POSITION.labels(partitionLabel);
    commitLatency = COMMIT_LATENCY.labels(partitionLabel);
    appendLatency = WRITE_LATENCY.labels(partitionLabel);
    exportingRate = EXPORTING_RATE.labels(partitionLabel);
    writeRateMaxLimit = WRITE_RATE_MAX_LIMIT.labels(partitionLabel);
    writeRateLimit = WRITE_RATE_LIMIT.labels(partitionLabel);
    partitionLoad = PARTITION_LOAD.labels(partitionLabel);
  }

  public void increaseInflightAppends() {
    inflightAppends.inc();
  }

  public void decreaseInflightAppends() {
    inflightAppends.dec();
  }

  public void setInflightRequests(final int count) {
    inflightRequests.set(count);
  }

  public void setRequestLimit(final int limit) {
    requestLimit.set(limit);
  }

  public void increaseInflightRequests() {
    inflightRequests.inc();
  }

  public void decreaseInflightRequests() {
    inflightRequests.dec();
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

  public void remove() {
    TOTAL_DEFERRED_APPEND_COUNT.remove(partitionLabel);
    TOTAL_APPEND_TRY_COUNT.remove(partitionLabel);
    INFLIGHT_APPENDS.remove(partitionLabel);
    INFLIGHT_REQUESTS.remove(partitionLabel);
    REQUEST_LIMIT.remove(partitionLabel);
    LAST_COMMITTED_POSITION.remove(partitionLabel);
    LAST_WRITTEN_POSITION.remove(partitionLabel);
    COMMIT_LATENCY.remove(partitionLabel);
    WRITE_LATENCY.remove(partitionLabel);
    EXPORTING_RATE.remove(partitionLabel);
    WRITE_RATE_MAX_LIMIT.remove(partitionLabel);
    WRITE_RATE_LIMIT.remove(partitionLabel);
    PARTITION_LOAD.remove(partitionLabel);
    for (final var contextLabel : FlowControlOutComeLabels.allContextLabels()) {
      for (final var reasonLabel : FlowControlOutComeLabels.allReasonLabels()) {
        FLOW_CONTROL_OUTCOME.remove(partitionLabel, contextLabel, reasonLabel);
      }
    }
  }

  public void flowControlAccepted(
      final WriteContext context, final List<LogAppendEntryMetadata> batchMetadata) {
    triedAppends.inc();
    if (context instanceof UserCommand) {
      receivedRequests.inc();
    }
    FLOW_CONTROL_OUTCOME
        .labels(partitionLabel, labelForContext(context), "accepted")
        .inc(batchMetadata.size());
  }

  public void flowControlRejected(
      final WriteContext context,
      final List<LogAppendEntryMetadata> batchMetadata,
      final Rejection reason) {
    triedAppends.inc();
    deferredAppends.inc();
    if (context instanceof UserCommand) {
      receivedRequests.inc();
      droppedRequests.inc();
    }
    FLOW_CONTROL_OUTCOME
        .labels(partitionLabel, labelForContext(context), labelForReason(reason))
        .inc(batchMetadata.size());
  }

  public void setPartitionLoad(final float load) {
    partitionLoad.set(load);
  }

  public void setExportingRate(final long value) {
    exportingRate.set(value);
  }

  public void setWriteRateMaxLimit(final long value) {
    writeRateMaxLimit.set(value);
  }

  public void setWriteRateLimit(final double value) {
    writeRateLimit.set(value);
  }

  static final class FlowControlOutComeLabels {

    private FlowControlOutComeLabels() {}

    static String[] allReasonLabels() {
      return EnumSet.allOf(Rejection.class).stream()
          .map(FlowControlOutComeLabels::labelForReason)
          .toArray(String[]::new);
    }

    static String[] allContextLabels() {
      final var labels = WriteContextLabel.values();
      final var labelNames = new String[labels.length];
      for (var i = 0; i < labels.length; i++) {
        labelNames[i] = labels[i].labelName;
      }
      return labelNames;
    }

    static String labelForReason(final Rejection reason) {
      return switch (reason) {
        case WriteRateLimitExhausted -> "writeRateLimitExhausted";
        case RequestLimitExhausted -> "requestLimitExhausted";
      };
    }

    static String labelForContext(final WriteContext context) {
      return switch (context) {
        case final UserCommand ignored -> WriteContextLabel.UserCommand.labelName;
        case final ProcessingResult ignored -> WriteContextLabel.ProcessingResult.labelName;
        case final InterPartition ignored -> WriteContextLabel.InterPartition.labelName;
        case final Scheduled ignored -> WriteContextLabel.Scheduled.labelName;
        case final Internal ignored -> WriteContextLabel.Internal.labelName;
      };
    }

    private enum WriteContextLabel {
      UserCommand("userCommand"),
      ProcessingResult("processingResult"),
      InterPartition("interPartition"),
      Scheduled("scheduled"),
      Internal("internal");
      private final String labelName;

      WriteContextLabel(final String labelName) {
        this.labelName = labelName;
      }
    }
  }
}
