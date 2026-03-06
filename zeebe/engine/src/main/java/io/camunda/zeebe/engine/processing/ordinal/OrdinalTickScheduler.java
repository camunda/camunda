/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import static java.util.Optional.ofNullable;

import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Schedules an {@link OrdinalIntent#TICK} command once per minute. On each execution the command
 * processor will emit an {@link OrdinalIntent#TICKED} event that increments the persisted ordinal
 * value and records the wall-clock time at which the tick occurred.
 */
public final class OrdinalTickScheduler implements Task, StreamProcessorLifecycleAware {

  private static final Duration TICK_INTERVAL = Duration.ofMinutes(1);

  private final InstantSource clock;
  private volatile boolean shouldReschedule = false;
  private ReadonlyStreamProcessorContext processingContext;
  private final AtomicReference<ScheduledTask> scheduledTask = new AtomicReference<>(null);

  public OrdinalTickScheduler(final InstantSource clock) {
    this.clock = clock;
  }

  public void schedule(final boolean immediately) {
    final ScheduledTask nextTask;
    if (immediately) {
      nextTask = processingContext.getScheduleService().runAtAsync(0L, this);
    } else {
      nextTask =
          processingContext
              .getScheduleService()
              .runAt(clock.millis() + TICK_INTERVAL.toMillis(), this);
    }
    ofNullable(scheduledTask.getAndSet(nextTask)).ifPresent(ScheduledTask::cancel);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    taskResultBuilder.appendCommandRecord(OrdinalIntent.TICK, new OrdinalRecord());

    if (shouldReschedule) {
      schedule(false);
    }

    return taskResultBuilder.build();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
    shouldReschedule = true;
    schedule(true);
  }

  @Override
  public void onClose() {
    shouldReschedule = false;
  }

  @Override
  public void onFailed() {
    shouldReschedule = false;
  }

  @Override
  public void onPaused() {
    shouldReschedule = false;
  }

  @Override
  public void onResumed() {
    shouldReschedule = true;
    schedule(true);
  }

  // Package-private setters for tests
  void setProcessingContext(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
  }

  void setShouldReschedule(final boolean shouldReschedule) {
    this.shouldReschedule = shouldReschedule;
  }
}
