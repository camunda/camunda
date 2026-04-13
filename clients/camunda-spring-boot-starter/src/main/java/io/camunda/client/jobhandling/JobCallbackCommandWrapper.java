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

import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.command.JobCallbackFinalCommandStep;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.MetricsRecorder.CounterMetricsContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobCallbackCommandWrapper {
  public static final Set<Integer> REST_RETRYABLE_CODES = Set.of(429, 502, 503, 504);
  public static final Set<Integer> REST_IGNORABLE_CODES = Set.of(404);
  public static final Set<Status.Code> RETRIABLE_CODES =
      EnumSet.of(
          Status.Code.CANCELLED,
          Status.Code.DEADLINE_EXCEEDED,
          Status.Code.RESOURCE_EXHAUSTED,
          Status.Code.ABORTED,
          Status.Code.UNAVAILABLE,
          Status.Code.DATA_LOSS);
  public static final Set<Status.Code> IGNORABLE_FAILURE_CODES = EnumSet.of(Status.Code.NOT_FOUND);
  private static final Logger LOG = LoggerFactory.getLogger(JobCallbackCommandWrapper.class);
  private final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser;
  private final JobCallbackFinalCommandStep<?> command;
  private final long deadline;
  private final MetricsRecorder metricsRecorder;
  private final CounterMetricsContext metricsContext;
  private final int maxRetries;
  private final BackoffSupplier backoffSupplier;
  private final ScheduledExecutorService scheduledExecutorService;
  private final CompletableFuture<CommandOutcome> resultFuture = new CompletableFuture<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final Runnable action;
  private final AtomicLong currentRetryDelay = new AtomicLong(50L);
  private final AtomicInteger invocationCounter = new AtomicInteger(0);

  public JobCallbackCommandWrapper(
      final JobCallbackFinalCommandStep<?> command,
      final long deadline,
      final MetricsRecorder metricsRecorder,
      final CounterMetricsContext metricsContext,
      final int maxRetries,
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService scheduledExecutorService) {
    this(
        command,
        deadline,
        metricsRecorder,
        metricsContext,
        maxRetries,
        findIncreaser(command),
        backoffSupplier,
        scheduledExecutorService);
  }

  JobCallbackCommandWrapper(
      final JobCallbackFinalCommandStep<?> command,
      final long deadline,
      final MetricsRecorder metricsRecorder,
      final CounterMetricsContext metricsContext,
      final int maxRetries,
      final BiConsumer<MetricsRecorder, CounterMetricsContext> increaser,
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService scheduledExecutorService) {
    this.command = command;
    this.deadline = deadline;
    this.maxRetries = maxRetries;
    this.metricsRecorder = metricsRecorder;
    this.metricsContext = metricsContext;
    this.increaser = increaser;
    this.backoffSupplier = backoffSupplier;
    this.scheduledExecutorService = scheduledExecutorService;
    action = this::doExecute;
  }

  private static BiConsumer<MetricsRecorder, CounterMetricsContext> findIncreaser(
      final JobCallbackFinalCommandStep<?> command) {
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
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("JobCallbackCommandWrapper has already been executed");
    }

    action.run();

    return resultFuture;
  }

  private void doExecute() {
    try {
      invocationCounter.getAndIncrement();
      command
          .send()
          .whenComplete(
              (response, throwable) -> {
                try {
                  if (throwable != null) {
                    handleError(throwable);
                  } else {
                    increaser.accept(metricsRecorder, metricsContext);
                    resultFuture.complete(
                        new CommandOutcome.Completed(response, invocationCounter.get()));
                  }
                } catch (final RuntimeException e) {
                  LOG.warn(
                      "An unexpected error occurred during handling of job callback command", e);
                  resultFuture.completeExceptionally(e);
                }
              });
    } catch (final RuntimeException e) {
      LOG.warn("An unexpected error occurred during handling of job callback command", e);
      resultFuture.completeExceptionally(e);
    }
  }

  private void handleError(final Throwable throwable) {
    final CommandOutcome outcome = handleCommandError(throwable);

    // a retried command has invoked scheduleExecution already, so we do not complete the
    // future here, it will be completed by the retry action
    if (outcome != null) {
      resultFuture.complete(outcome);
    }
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
    return invocationCounter.get();
  }

  public boolean hasMoreRetries() {
    if (jobDeadlineExceeded()) {
      // it does not make much sense to retry if the deadline is over, the job will be assigned to
      // another worker anyway
      return false;
    }
    return (invocationCounter.get() < maxRetries);
  }

  private boolean jobDeadlineExceeded() {
    return (System.currentTimeMillis() > deadline);
  }

  private CommandOutcome handleCommandError(final Throwable throwable) {
    if (throwable instanceof final StatusRuntimeException exception) {
      return handleGrpcError(exception);
    }
    if (throwable instanceof final ClientHttpException exception) {
      return handleRestError(exception);
    }
    LOG.error("Failed to execute {} due to unexpected exception", this, throwable);
    return new CommandOutcome.Failed(throwable, getAttempts());
  }

  private CommandOutcome handleRestError(final ClientHttpException exception) {
    final int code = exception.code();
    return handleError(
        exception,
        "http status code",
        code,
        REST_IGNORABLE_CODES::contains,
        REST_RETRYABLE_CODES::contains);
  }

  private CommandOutcome handleGrpcError(final StatusRuntimeException exception) {
    final Status.Code code = exception.getStatus().getCode();
    return handleError(
        exception,
        "gRPC status code",
        code,
        IGNORABLE_FAILURE_CODES::contains,
        RETRIABLE_CODES::contains);
  }

  private <T> CommandOutcome handleError(
      final Exception exception,
      final String codeType,
      final T code,
      final Predicate<T> ignorableCodes,
      final Predicate<T> retryableCodes) {
    if (ignorableCodes.test(code)) {
      LOG.debug("Ignoring {} with {} '{}'", command, codeType, code);
      return new CommandOutcome.Ignored(exception, getAttempts());
    }

    if (retryableCodes.test(code)) {
      if (!hasMoreRetries()) {
        LOG.error(
            "Failed to execute {} after {} attempts, {} '{}'",
            this,
            getAttempts(),
            codeType,
            code,
            exception);
        return new CommandOutcome.Failed(exception, getAttempts());
      }

      scheduleRetry(codeType, code);
      return null; // retry scheduled
    }
    LOG.error(
        "Failed to execute {} due to non-retriable {} '{}'", command, codeType, code, exception);
    return new CommandOutcome.Failed(exception, getAttempts());
  }

  private <T> void scheduleRetry(final String codeType, final T code) {
    currentRetryDelay.getAndUpdate(backoffSupplier::supplyRetryDelay);
    LOG.warn("Retrying {} after {} '{}' with backoff", command, codeType, code);
    scheduledExecutorService.schedule(action, currentRetryDelay.get(), TimeUnit.MILLISECONDS);
  }
}
