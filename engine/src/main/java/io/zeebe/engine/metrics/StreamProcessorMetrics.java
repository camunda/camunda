/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.zeebe.protocol.record.RecordType;

public class StreamProcessorMetrics {

  private static final Counter STREAM_PROCESSOR_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("stream_processor_events_total")
          .help("Number of events processed by stream processor")
          .labelNames("action", "partition")
          .register();

  private static final Histogram PROCESSING_LATENCY =
      Histogram.build()
          .namespace("zeebe")
          .name("stream_processor_latency")
          .help("Latency of processing in seconds")
          .labelNames("recordType", "partition")
          .register();

  private final String partitionIdLabel;

  public StreamProcessorMetrics(int partitionId) {
    this.partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(String action) {
    STREAM_PROCESSOR_EVENTS.labels(action, partitionIdLabel).inc();
  }

  public void processingLatency(RecordType recordType, long written, long processed) {
    PROCESSING_LATENCY
        .labels(recordType.name(), partitionIdLabel)
        .observe((processed - written) / 1000f);
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
}
