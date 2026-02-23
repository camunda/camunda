/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.usage;

import static java.util.Optional.ofNullable;

import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsageMetricsCheckScheduler implements Task, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsCheckScheduler.class);

  private final Duration exportInterval;
  private final InstantSource clock;
  private ReadonlyStreamProcessorContext processingContext;
  private volatile boolean shouldReschedule = false;
  private final AtomicReference<ScheduledTask> scheduledTask = new AtomicReference<>(null);

  public UsageMetricsCheckScheduler(final Duration exportInterval, final InstantSource clock) {
    this.exportInterval = exportInterval;
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
              .runAt(clock.millis() + exportInterval.toMillis(), this);
      LOG.trace("UsageMetricsCheckScheduler scheduled");
    }

    ofNullable(scheduledTask.getAndSet(nextTask)).ifPresent(ScheduledTask::cancel);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.trace("UsageMetricsCheckScheduler running...");

    taskResultBuilder.appendCommandRecord(
        UsageMetricIntent.EXPORT, new UsageMetricRecord().setEventType(EventType.NONE));

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
