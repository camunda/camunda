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

import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.JobCallbackFinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobCallbackCommandWrapper {
  private static final Logger LOG = LoggerFactory.getLogger(JobCallbackCommandWrapper.class);
  final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser;
  private final JobCallbackFinalCommandStep<?> command;
  private final long deadline;
  private final JobCallbackCommandExceptionHandlingStrategy
      jobCallbackCommandExceptionHandlingStrategy;
  private final MetricsRecorder metricsRecorder;
  private final CounterMetricsContext metricsContext;
  private long currentRetryDelay = 50L;
  private int invocationCounter = 0;
  private final int maxRetries;
  private final CompletableFuture<CommandOutcome> resultFuture = new CompletableFuture<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private Runnable retryAction;

  public JobCallbackCommandWrapper(
      final JobCallbackFinalCommandStep<?> command,
      final long deadline,
      final JobCallbackCommandExceptionHandlingStrategy jobCallbackCommandExceptionHandlingStrategy,
      final MetricsRecorder metricsRecorder,
      final CounterMetricsContext metricsContext,
      final int maxRetries) {
    this.command = command;
    this.deadline = deadline;
    this.jobCallbackCommandExceptionHandlingStrategy = jobCallbackCommandExceptionHandlingStrategy;
    this.maxRetries = maxRetries;
    this.metricsRecorder = metricsRecorder;
    this.metricsContext = metricsContext;
    increaser = findIncreaser();
  }

  private BiConsumer<MetricsRecorder, CounterMetricsContext> findIncreaser() {
    if (command instanceof CompleteJobCommandStep1) {
      return MetricsRecorder::increaseCompleted;
    }
    if (command instanceof FailJobCommandStep2) {
      return MetricsRecorder::increaseFailed;
    }
    if (command instanceof ThrowErrorCommandStep2) {
      return MetricsRecorder::increaseBpmnError;
    }
    LOG.warn("Unknown command type, no metrics will be increased: {}", command.getClass());
    return (m, c) -> {};
  }

  public CompletableFuture<CommandOutcome> executeAsync() {
    return execute(() -> doExecute(increaser));
  }

  private CompletableFuture<CommandOutcome> execute(final Runnable action) {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("JobCallbackCommandWrapper has already been executed");
    }

    retryAction = action;
    action.run();

    return resultFuture;
  }

  private void doExecute(final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser) {
    invocationCounter++;
    command
        .send()
        .whenComplete(
            (response, throwable) -> {
              if (throwable != null) {
                handleError(throwable);
              } else {
                increaser.accept(metricsRecorder, metricsContext);
                resultFuture.complete(new CommandOutcome.Completed(response, invocationCounter));
              }
            });
  }

  private void handleError(final Throwable throwable) {
    final CommandOutcome outcome =
        jobCallbackCommandExceptionHandlingStrategy.handleCommandError(this, throwable);

    // a retried command has invoked scheduleExecutionUsing already, so we do not complete the
    // future here, it will be completed by the retry action
    if (outcome != null) {
      resultFuture.complete(outcome);
    }
  }

  public void increaseBackoffUsing(final BackoffSupplier backoffSupplier) {
    currentRetryDelay = backoffSupplier.supplyRetryDelay(currentRetryDelay);
  }

  public void scheduleExecutionUsing(final ScheduledExecutorService scheduledExecutorService) {
    scheduledExecutorService.schedule(retryAction, currentRetryDelay, TimeUnit.MILLISECONDS);
  }

  @Override
  public String toString() {
    return "{"
        + "command="
        + command.getClass()
        + ", deadline="
        + deadline
        + ", currentRetryDelay="
        + currentRetryDelay
        + '}';
  }

  public int getAttempts() {
    return invocationCounter;
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
    return (System.currentTimeMillis() > deadline);
  }
}
