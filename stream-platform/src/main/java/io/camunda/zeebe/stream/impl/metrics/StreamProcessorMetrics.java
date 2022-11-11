/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.protocol.record.RecordType;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

public final class StreamProcessorMetrics {

  private static final String LABEL_NAME_PARTITION = "partition";
  private static final String LABEL_NAME_RECORD_TYPE = "recordType";
  private static final String LABEL_NAME_ACTION = "action";

  private static final String LABEL_WRITTEN = "written";
  private static final String LABEL_SKIPPED = "skipped";
  private static final String LABEL_PROCESSED = "processed";
  private static final String NAMESPACE = "zeebe";

  private static final Counter STREAM_PROCESSOR_EVENTS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("stream_processor_records_total")
          .help("Number of records processed by stream processor")
          .labelNames(LABEL_NAME_ACTION, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge LAST_PROCESSED_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_last_processed_position")
          .help("The last position the stream processor has processed.")
          .labelNames(LABEL_NAME_PARTITION)
          .register();

  private static final Histogram PROCESSING_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_latency")
          .help(
              "Time between a command is written until it is picked up for processing (in seconds)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();
  private static final Histogram PROCESSING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_processing_duration")
          .help("Time for processing a record (in seconds)")
          .labelNames(LABEL_NAME_RECORD_TYPE, LABEL_NAME_PARTITION)
          .register();

  private static final Gauge STARTUP_RECOVERY_TIME =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_startup_recovery_time")
          .help("Time taken for startup and recovery of stream processor (in ms)")
          .labelNames(LABEL_NAME_PARTITION)
          .register();
  private final String partitionIdLabel;

  public StreamProcessorMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(final String action) {
    STREAM_PROCESSOR_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void processingLatency(final long written, final long processed) {
    PROCESSING_LATENCY.labels(partitionIdLabel).observe((processed - written) / 1000f);
  }

  public Histogram.Timer startProcessingDurationTimer(final RecordType recordType) {
    return PROCESSING_DURATION.labels(recordType.name(), partitionIdLabel).startTimer();
  }

  /** We only process commands. */
  public void commandsProcessed() {
    event(LABEL_PROCESSED);
  }

  /**
   * We write various type of records. The positions are always increasing and incremented by 1 for
   * one record.
   */
  public void recordsWritten(final long amount) {
    if (amount < 1) {
      return;
    }

    STREAM_PROCESSOR_EVENTS.labels(LABEL_WRITTEN, partitionIdLabel).inc(amount);
  }

  /** We skip events on processing. */
  public void eventSkipped() {
    event(LABEL_SKIPPED);
  }

  public Gauge.Timer startRecoveryTimer() {
    return STARTUP_RECOVERY_TIME.labels(partitionIdLabel).startTimer();
  }

  public void setLastProcessedPosition(final long position) {
    LAST_PROCESSED_POSITION.labels(partitionIdLabel).set(position);
  }
}
