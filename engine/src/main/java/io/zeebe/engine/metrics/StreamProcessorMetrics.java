/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.zeebe.protocol.record.RecordType;

public final class StreamProcessorMetrics {

  private static final String NAMESPACE = "zeebe";
  private static final Counter STREAM_PROCESSOR_EVENTS =
      Counter.build()
          .namespace(NAMESPACE)
          .name("stream_processor_events_total")
          .help("Number of events processed by stream processor")
          .labelNames("action", "partition")
          .register();

  private static final Gauge LAST_PROCESSED_POSITION =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_last_processed_position")
          .help("The last position the stream processor has processed.")
          .labelNames("partition")
          .register();

  private static final Histogram PROCESSING_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_latency")
          .help("Time between a record is written until it is picked up for processing (in seconds)")
          .labelNames("recordType", "partition")
          .register();
  private static final Histogram PROCESSING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_processing_duration")
          .help("Time for processing a record (in seconds)")
          .labelNames("recordType", "partition")
          .register();

  private static final Gauge STARTUP_RECOVERY_TIME =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("stream_processor_startup_recovery_time")
          .help("Time taken for startup and recovery of stream processor (in ms)")
          .labelNames("partition")
          .register();

  private final String partitionIdLabel;

  public StreamProcessorMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(final String action) {
    STREAM_PROCESSOR_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void processingLatency(
      final RecordType recordType, final long written, final long processed) {
    PROCESSING_LATENCY
        .labels(recordType.name(), partitionIdLabel)
        .observe((processed - written) / 1000f);
  }

  public void processingDuration(
      final RecordType recordType, final long started, final long processed) {
    PROCESSING_DURATION
        .labels(recordType.name(), partitionIdLabel)
        .observe((processed - started) / 1000f);
  }

  public void eventProcessed() {
    event("processed");
  }

  public void eventWritten() {
    event("written");
  }

  public void eventSkipped() {
    event("skipped");
  }

  public void recoveryTime(final long durationMillis) {
    STARTUP_RECOVERY_TIME.labels(partitionIdLabel).set(durationMillis);
  }

  public void setLastProcessedPosition(final long position) {
    LAST_PROCESSED_POSITION.labels(partitionIdLabel).set(position);
  }
}
