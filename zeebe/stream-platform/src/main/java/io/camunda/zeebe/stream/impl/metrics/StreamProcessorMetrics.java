/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.metrics;

<<<<<<< HEAD
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
=======
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
>>>>>>> 228ec46d (refactor: migrate StreamProcessorMetrics to Micrometer)

public final class StreamProcessorMetrics {

  private final AtomicLong startupRecoveryTime = new AtomicLong();
  private final AtomicInteger processorState = new AtomicInteger();

  private final MeterRegistry registry;

  public StreamProcessorMetrics(final MeterRegistry registry) {
    this.registry = registry;

<<<<<<< HEAD
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
  private static final String LABEL_NAME_VALUE_TYPE = "valueType";
  private static final String LABEL_NAME_INTENT = "intent";
  private static final Histogram PROCESSING_DURATION =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("stream_processor_processing_duration")
          .help("Time for processing a record (in seconds)")
          .labelNames(LABEL_NAME_PARTITION, LABEL_NAME_VALUE_TYPE, LABEL_NAME_INTENT)
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
=======
    registerStartupRecoveryTime();
    registerProcessorState();
  }

  public void setStreamProcessorInitial() {
    processorState.set(0);
  }

  public void setStreamProcessorReplay() {
    processorState.set(1);
  }

  public void setStreamProcessorProcessing() {
    processorState.set(2);
  }

  public void setStreamProcessorFailed() {
    processorState.set(3);
  }

  public void setStreamProcessorPaused() {
    processorState.set(4);
>>>>>>> 228ec46d (refactor: migrate StreamProcessorMetrics to Micrometer)
  }

  public CloseableSilently startRecoveryTimer() {
    return MicrometerUtil.timer(
        startupRecoveryTime::set, TimeUnit.MILLISECONDS, registry.config().clock());
  }
<<<<<<< HEAD
=======

  public void initializeProcessorPhase(final Phase phase) {
    switch (phase) {
      case INITIAL:
        setStreamProcessorInitial();
        break;
      case REPLAY:
        setStreamProcessorReplay();
        break;
      case PROCESSING:
        setStreamProcessorProcessing();
        break;
      case PAUSED:
        setStreamProcessorPaused();
        break;
      default:
        setStreamProcessorFailed();
    }
  }

  private void registerStartupRecoveryTime() {
    final var meterDoc = StreamMetricsDoc.PROCESSOR_STATE;
    TimeGauge.builder(
            meterDoc.getName(), startupRecoveryTime, TimeUnit.MILLISECONDS, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }

  private void registerProcessorState() {
    final var meterDoc = StreamMetricsDoc.PROCESSOR_STATE;
    Gauge.builder(meterDoc.getName(), processorState, AtomicInteger::intValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }
>>>>>>> 228ec46d (refactor: migrate StreamProcessorMetrics to Micrometer)
}
