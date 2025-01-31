/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.metrics;

import io.camunda.zeebe.stream.impl.ProcessingStateMachine.ErrorHandlingPhase;
import io.camunda.zeebe.stream.impl.metrics.StreamMetricsDoc.ErrorHandlingPhaseKeys;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.micrometer.EnumMeter;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class ProcessingMetrics {

  private final Clock clock;
  private final Timer batchProcessingDuration;
  private final Timer batchProcessingPostCommitTasks;
  private final DistributionSummary batchProcessingCommands;
  private final Counter batchProcessingRetries;
  private final EnumMeter<ErrorHandlingPhase> errorHandlingPhase;

  public ProcessingMetrics(final MeterRegistry registry) {
    clock = registry.config().clock();

    batchProcessingDuration = registerTimer(StreamMetricsDoc.BATCH_PROCESSING_DURATION, registry);
    batchProcessingPostCommitTasks =
        registerTimer(StreamMetricsDoc.BATCH_PROCESSING_POST_COMMIT_TASKS, registry);
    batchProcessingCommands = registerBatchProcessingCommands(registry);
    batchProcessingRetries = registerBatchProcessingRetries(registry);
    errorHandlingPhase =
        EnumMeter.register(
            ErrorHandlingPhase.class,
            StreamMetricsDoc.ERROR_HANDLING_PHASE,
            ErrorHandlingPhaseKeys.ERROR_HANDLING_PHASE,
            registry);

    // initialize as no error to start with
    errorHandlingPhase.state(ErrorHandlingPhase.NO_ERROR);
  }

  public CloseableSilently startBatchProcessingDurationTimer() {
    return MicrometerUtil.timer(batchProcessingDuration, Timer.start(clock));
  }

  public void observeCommandCount(final int commandCount) {
    batchProcessingCommands.record(commandCount);
  }

  public void countRetry() {
    batchProcessingRetries.increment();
  }

  public CloseableSilently startBatchProcessingPostCommitTasksTimer() {
    return MicrometerUtil.timer(batchProcessingPostCommitTasks, Timer.start(clock));
  }

  public void errorHandlingPhase(final ErrorHandlingPhase phase) {
    errorHandlingPhase.state(phase);
  }

  private DistributionSummary registerBatchProcessingCommands(final MeterRegistry registry) {
    final DistributionSummary batchProcessingCommands;
    final var commandsDoc = StreamMetricsDoc.BATCH_PROCESSING_COMMANDS;
    batchProcessingCommands =
        DistributionSummary.builder(commandsDoc.getName())
            .description(commandsDoc.getDescription())
            .serviceLevelObjectives(commandsDoc.getDistributionSLOs())
            .register(registry);
    return batchProcessingCommands;
  }

  private Counter registerBatchProcessingRetries(final MeterRegistry registry) {
    final Counter batchProcessingRetries;
    final var retriesDoc = StreamMetricsDoc.BATCH_PROCESSING_RETRIES;
    batchProcessingRetries =
        Counter.builder(retriesDoc.getName())
            .description(retriesDoc.getDescription())
            .register(registry);
    return batchProcessingRetries;
  }

  private Timer registerTimer(final StreamMetricsDoc meterDoc, final MeterRegistry registry) {
    return Timer.builder(meterDoc.getName())
        .description(meterDoc.getDescription())
        .serviceLevelObjectives(meterDoc.getTimerSLOs())
        .register(registry);
  }
}
