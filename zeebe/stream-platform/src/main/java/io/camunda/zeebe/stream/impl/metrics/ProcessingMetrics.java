/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.impl.ProcessingStateMachine.ErrorHandlingPhase;
import io.camunda.zeebe.stream.impl.metrics.StreamMetricsDoc.ErrorHandlingPhaseKeys;
import io.camunda.zeebe.stream.impl.metrics.StreamMetricsDoc.ProcessingDurationKeys;
import io.camunda.zeebe.stream.impl.metrics.StreamMetricsDoc.StreamProcessorActionKeys;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.EnumMeter;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessingMetrics {
  private static final String ACTION_WRITTEN = "written";
  private static final String ACTION_SKIPPED = "skipped";
  private static final String ACTION_PROCESSED = "processed";

  private final AtomicLong lastProcessedPosition = new AtomicLong();
  private final Table<ValueType, Intent, Timer> processingDuration = Table.simple();
  private final Map<String, Counter> streamProcessorEvents = new HashMap<>();

  private final MeterRegistry registry;
  private final Timer batchProcessingDuration;
  private final Timer batchProcessingPostCommitTasks;
  private final DistributionSummary batchProcessingCommands;
  private final Counter batchProcessingRetries;
  private final EnumMeter<ErrorHandlingPhase> errorHandlingPhase;
  private final Timer processingLatency;

  public ProcessingMetrics(final MeterRegistry registry) {
    this.registry = registry;

    registerLastProcessedPosition();
    batchProcessingDuration = registerTimer(StreamMetricsDoc.BATCH_PROCESSING_DURATION);
    batchProcessingPostCommitTasks =
        registerTimer(StreamMetricsDoc.BATCH_PROCESSING_POST_COMMIT_TASKS);
    batchProcessingCommands = registerBatchProcessingCommands();
    batchProcessingRetries = registerBatchProcessingRetries();
    errorHandlingPhase =
        EnumMeter.register(
            ErrorHandlingPhase.class,
            StreamMetricsDoc.ERROR_HANDLING_PHASE,
            ErrorHandlingPhaseKeys.ERROR_HANDLING_PHASE,
            registry);
    processingLatency = registerProcessingLatency();

    // initialize as no error to start with
    errorHandlingPhase.state(ErrorHandlingPhase.NO_ERROR);
  }

  public CloseableSilently startBatchProcessingDurationTimer() {
    return MicrometerUtil.timer(batchProcessingDuration, Timer.start(registry.config().clock()));
  }

  public void observeCommandCount(final int commandCount) {
    batchProcessingCommands.record(commandCount);
  }

  public void countRetry() {
    batchProcessingRetries.increment();
  }

  public CloseableSilently startBatchProcessingPostCommitTasksTimer() {
    return MicrometerUtil.timer(
        batchProcessingPostCommitTasks, Timer.start(registry.config().clock()));
  }

  public void errorHandlingPhase(final ErrorHandlingPhase phase) {
    errorHandlingPhase.state(phase);
  }

  public void processingLatency(final long written, final long processed) {
    processingLatency.record(processed - written, TimeUnit.MILLISECONDS);
  }

  public CloseableSilently startProcessingDurationTimer(
      final ValueType valueType, final Intent intent) {
    final var timer =
        processingDuration.computeIfAbsent(
            valueType, intent, this::registerProcessingDurationTimer);
    return MicrometerUtil.timer(timer, Timer.start(registry.config().clock()));
  }

  /** We only process commands. */
  public void commandsProcessed() {
    event(ACTION_PROCESSED);
  }

  /**
   * We write various type of records. The positions are always increasing and incremented by 1 for
   * one record.
   */
  public void recordsWritten(final long amount) {
    if (amount < 1) {
      return;
    }

    countStreamProcessorEvent(ACTION_WRITTEN, amount);
  }

  /** We skip events on processing. */
  public void eventSkipped() {
    event(ACTION_SKIPPED);
  }

  public void setLastProcessedPosition(final long position) {
    lastProcessedPosition.set(position);
  }

  private DistributionSummary registerBatchProcessingCommands() {
    final DistributionSummary batchProcessingCommands;
    final var commandsDoc = StreamMetricsDoc.BATCH_PROCESSING_COMMANDS;
    batchProcessingCommands =
        DistributionSummary.builder(commandsDoc.getName())
            .description(commandsDoc.getDescription())
            .serviceLevelObjectives(commandsDoc.getDistributionSLOs())
            .register(registry);
    return batchProcessingCommands;
  }

  private Counter registerBatchProcessingRetries() {
    final Counter batchProcessingRetries;
    final var retriesDoc = StreamMetricsDoc.BATCH_PROCESSING_RETRIES;
    batchProcessingRetries =
        Counter.builder(retriesDoc.getName())
            .description(retriesDoc.getDescription())
            .register(registry);
    return batchProcessingRetries;
  }

  private Timer registerTimer(final StreamMetricsDoc meterDoc) {
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }

  private void registerLastProcessedPosition() {
    final var meterDoc = StreamMetricsDoc.LAST_PROCESSED_POSITION;
    Gauge.builder(meterDoc.getName(), lastProcessedPosition, AtomicLong::longValue)
        .description(meterDoc.getDescription())
        .register(registry);
  }

  private void event(final String action) {
    countStreamProcessorEvent(action, 1);
  }

  private void countStreamProcessorEvent(final String action, final long count) {
    streamProcessorEvents
        .computeIfAbsent(action, this::registerStreamProcessorEventCounter)
        .increment(count);
  }

  private Counter registerStreamProcessorEventCounter(final String action) {
    final var meterDoc = StreamMetricsDoc.STREAM_PROCESSOR_EVENTS;
    return Counter.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .tag(StreamProcessorActionKeys.ACTION.asString(), action)
        .register(registry);
  }

  private Timer registerProcessingLatency() {
    final var meterDoc = StreamMetricsDoc.PROCESSING_LATENCY;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }

  private Timer registerProcessingDurationTimer(final ValueType valueType, final Intent intent) {
    final var meterDoc = StreamMetricsDoc.PROCESSING_DURATION;
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .tag(ProcessingDurationKeys.VALUE_TYPE.asString(), valueType.name())
        .tag(ProcessingDurationKeys.INTENT.asString(), intent.name())
        .register(registry);
  }
}
