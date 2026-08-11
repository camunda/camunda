/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobBatchDeliveryState;
import io.camunda.zeebe.engine.state.immutable.JobBatchDeliveryState.DeliveryDeadlineIndex;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import org.agrona.collections.MutableInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yields JobBatch activations whose delivery ACK never arrived, by appending {@link
 * JobBatchIntent#REJECT} for each timed-out pending delivery.
 */
final class JobBatchDeliveryAckCheckScheduler implements Task, StreamProcessorLifecycleAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(JobBatchDeliveryAckCheckScheduler.class);

  private boolean shouldReschedule = false;
  private long executionTimestamp = -1;
  private DeliveryDeadlineIndex startAtIndex = null;

  private final JobBatchDeliveryState state;
  private ReadonlyStreamProcessorContext processingContext;
  private final Duration pollingInterval;
  private final int batchLimit;
  private final InstantSource clock;
  private final JobBatchRecord rejectCommand = new JobBatchRecord();

  JobBatchDeliveryAckCheckScheduler(
      final JobBatchDeliveryState state,
      final Duration pollingInterval,
      final int batchLimit,
      final InstantSource clock) {
    this.state = state;
    this.pollingInterval = pollingInterval;
    this.batchLimit = batchLimit;
    this.clock = clock;
  }

  public void schedule(final Duration idleInterval) {
    if (shouldReschedule) {
      processingContext.getScheduleService().runAt(clock.millis() + idleInterval.toMillis(), this);
    }
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    if (executionTimestamp == -1) {
      executionTimestamp = clock.millis();
    }

    final var counter = new MutableInteger(0);
    final DeliveryDeadlineIndex lastVisitedIndex =
        state.forEachTimedOutDelivery(
            executionTimestamp,
            startAtIndex,
            (attemptKey, pending) -> {
              if (counter.getAndIncrement() >= batchLimit) {
                return false;
              }

              rejectCommand.reset();
              rejectCommand.setType(pending.getType());
              rejectCommand.setDeliveryAttemptKey(attemptKey);
              rejectCommand.setDeliveryDeadline(pending.getDeliveryDeadline());
              for (final long jobKey : pending.getJobKeys()) {
                rejectCommand.jobKeys().add().setValue(jobKey);
              }
              return taskResultBuilder.appendCommandRecord(
                  attemptKey, JobBatchIntent.REJECT, rejectCommand);
            });

    if (lastVisitedIndex != null) {
      LOG.trace(
          "Job batch delivery-ack checker yielded early. Will reschedule immediately from {}",
          lastVisitedIndex);
      startAtIndex = lastVisitedIndex;
      schedule(Duration.ZERO);
    } else {
      executionTimestamp = -1;
      startAtIndex = null;
      schedule(pollingInterval);
    }

    return taskResultBuilder.build();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
    shouldReschedule = true;
    schedule(pollingInterval);
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onResumed() {
    shouldReschedule = true;
    schedule(pollingInterval);
  }

  private void cancelTimer() {
    shouldReschedule = false;
  }
}
