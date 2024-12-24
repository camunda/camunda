/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.jobhandling;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandWrapper {

  private final FinalCommandStep<Void> command;
  private final ActivatedJob job;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  private long currentRetryDelay = 50L;
  private int invocationCounter = 0;
  private final int maxRetries;

  public CommandWrapper(
      final FinalCommandStep<Void> command,
      final ActivatedJob job,
      final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final int maxRetries) {
    this.command = command;
    this.job = job;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.metricsRecorder = metricsRecorder;
    this.maxRetries = maxRetries;
  }

  public void executeAsync() {
    invocationCounter++;
    command
        .send()
        .exceptionally(
            t -> {
              commandExceptionHandlingStrategy.handleCommandError(this, t);
              return null;
            });
  }

  public void executeAsyncWithMetrics(
      final String metricName, final String action, final String type) {
    invocationCounter++;
    command
        .send()
        .thenApply(
            result -> {
              metricsRecorder.increase(metricName, action, type);
              return result;
            })
        .exceptionally(
            t -> {
              commandExceptionHandlingStrategy.handleCommandError(this, t);
              return null;
            });
  }

  public void increaseBackoffUsing(final BackoffSupplier backoffSupplier) {
    currentRetryDelay = backoffSupplier.supplyRetryDelay(currentRetryDelay);
  }

  public void scheduleExecutionUsing(final ScheduledExecutorService scheduledExecutorService) {
    scheduledExecutorService.schedule(this::executeAsync, currentRetryDelay, TimeUnit.MILLISECONDS);
  }

  @Override
  public String toString() {
    return "{"
        + "command="
        + command.getClass()
        + ", job="
        + job
        + ", currentRetryDelay="
        + currentRetryDelay
        + '}';
  }

  public boolean hasMoreRetries() {
    if (jobDeadlineExceeded()) {
      // it does not make much sense to retry if the deadline is over, the job will be assigned to
      // another worker anyway
      return false;
    }
    return (invocationCounter < maxRetries);
  }

  private boolean jobDeadlineExceeded() {
    return (Instant.now().getEpochSecond() > job.getDeadline());
  }
}
