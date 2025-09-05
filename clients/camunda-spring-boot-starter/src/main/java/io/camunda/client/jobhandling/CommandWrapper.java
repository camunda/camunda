/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.jobhandling;

import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.MetricsRecorder;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandWrapper {

  private final FinalCommandStep<?> command;
  private final ActivatedJob job;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;

  private long currentRetryDelay = 50L;
  private int invocationCounter = 0;
  private final int maxRetries;

  public CommandWrapper(
      final FinalCommandStep<?> command,
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
