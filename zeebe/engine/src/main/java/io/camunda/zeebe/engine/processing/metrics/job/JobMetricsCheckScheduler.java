/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.job;

import static java.util.Optional.ofNullable;

import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
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

/**
 * Task that periodically sends a JOB_METRICS_BATCH EXPORT command to trigger the export of job
 * worker metrics.
 */
public class JobMetricsCheckScheduler implements Task, StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(JobMetricsCheckScheduler.class);

  private final Duration exportInterval;
  private final InstantSource clock;
  private ReadonlyStreamProcessorContext processingContext;
  private volatile boolean shouldReschedule = false;
  private final AtomicReference<ScheduledTask> scheduledTask = new AtomicReference<>(null);

  public JobMetricsCheckScheduler(final Duration exportInterval, final InstantSource clock) {
    this.exportInterval = exportInterval;
    this.clock = clock;
  }

  public JobMetricsCheckScheduler(final EngineConfiguration engineConfiguration, final InstantSource clock) {
    this(engineConfiguration.getJobMetricsExportInterval(), clock);
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
      LOG.trace("JobMetricsCheckScheduler scheduled");
    }

    ofNullable(scheduledTask.getAndSet(nextTask)).ifPresent(ScheduledTask::cancel);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.trace("JobMetricsCheckScheduler running...");
    final JobMetricsBatchRecord record = new JobMetricsBatchRecord();
    // trigger the export by writing the EXPORT command with an empty record
    // the JobMetricsBatchExportProcessor will use the JobMetricsState to fill in the data
    taskResultBuilder.appendCommandRecord(JobMetricsBatchIntent.EXPORT, record);

    if (shouldReschedule) {
      schedule(false);
    }

    return taskResultBuilder.build();
  }

  public void setProcessingContext(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
  }

  public void setShouldReschedule(final boolean shouldReschedule) {
    this.shouldReschedule = shouldReschedule;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.setProcessingContext(processingContext);
    this.setShouldReschedule(true);
    this.schedule(true);
  }

  @Override
  public void onClose() {
    this.setShouldReschedule(false);
  }

  @Override
  public void onFailed() {
    this.setShouldReschedule(false);
  }

  @Override
  public void onPaused() {
    this.setShouldReschedule(false);
  }

  @Override
  public void onResumed() {
    this.setShouldReschedule(true);
    this.schedule(true);
  }
}
