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
package io.camunda.zeebe.client.impl.worker;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.StreamJobsResponse;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.impl.Loggers;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;

@ThreadSafe
final class JobStreamerImpl implements JobStreamer {
  private static final Logger LOGGER = Loggers.JOB_WORKER_LOGGER;

  private final JobClient jobClient;
  private final String jobType;
  private final String workerName;
  private final Duration timeout;
  private final List<String> fetchVariables;
  private final List<String> tenantIds;
  private final Duration requestTimeout;
  private final BackoffSupplier backoffSupplier;
  private final ScheduledExecutorService executor;
  private final Lock streamLock;

  @GuardedBy("streamLock")
  private ZeebeFuture<StreamJobsResponse> streamControl;

  @GuardedBy("streamLock")
  private FinalCommandStep<StreamJobsResponse> command;

  @GuardedBy("streamLock")
  private boolean isClosed;

  @GuardedBy("streamLock")
  private long retryDelay;

  public JobStreamerImpl(
      final JobClient jobClient,
      final String jobType,
      final String workerName,
      final Duration timeout,
      final List<String> fetchVariables,
      final List<String> tenantIds,
      final Duration requestTimeout,
      final BackoffSupplier backoffSupplier,
      final ScheduledExecutorService executor) {
    this.jobClient = jobClient;
    this.jobType = jobType;
    this.workerName = workerName;
    this.timeout = timeout;
    this.fetchVariables = fetchVariables;
    this.tenantIds = tenantIds;
    this.requestTimeout = requestTimeout;
    this.backoffSupplier = backoffSupplier;
    this.executor = executor;

    streamLock = new ReentrantLock();
  }

  @Override
  public void close() {
    try {
      streamLock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    try {
      lockedClose();
    } finally {
      streamLock.unlock();
    }
  }

  @Override
  public boolean isOpen() {
    return !isClosed;
  }

  @Override
  public void openStreamer(final Consumer<ActivatedJob> jobConsumer) {
    final FinalCommandStep<StreamJobsResponse> command = buildCommand(jobConsumer);
    open(command);
  }

  private void open(final FinalCommandStep<StreamJobsResponse> command) {
    try {
      streamLock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    if (isClosed) {
      LOGGER.trace(
          "Skip opening stream '{}' for worker '{}' because it's closed", jobType, workerName);
      return;
    }

    try {
      this.command = command;
      lockedOpen();
    } finally {
      streamLock.unlock();
    }
  }

  private void handleStreamComplete(final Throwable error) {
    try {
      streamLock.lockInterruptibly();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    try {
      lockedHandleStreamComplete(error);
    } finally {
      streamLock.unlock();
    }
  }

  private FinalCommandStep<StreamJobsResponse> buildCommand(
      final Consumer<ActivatedJob> jobConsumer) {
    StreamJobsCommandStep3 command =
        jobClient
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(jobConsumer)
            .workerName(workerName)
            .tenantIds(tenantIds)
            .timeout(timeout);

    if (fetchVariables != null) {
      command = command.fetchVariables(fetchVariables);
    }

    return command.requestTimeout(requestTimeout);
  }

  @GuardedBy("streamLock")
  private void lockedClose() {
    LOGGER.debug("Closing job stream for type '{}' and worker '{}", jobType, workerName);
    isClosed = true;
    if (streamControl != null) {
      streamControl.cancel(true);
    }
  }

  @GuardedBy("streamLock")
  private void lockedOpen() {
    if (streamControl != null) {
      streamControl.cancel(true);
      streamControl = null;
    }

    final ZeebeFuture<StreamJobsResponse> control = command.send();
    control.whenCompleteAsync((ignored, error) -> handleStreamComplete(error), executor);
    streamControl = control;
    LOGGER.debug("Opened job stream of type '{}' for worker '{}'", jobType, workerName);
  }

  @GuardedBy("streamLock")
  private void lockedHandleStreamComplete(final Throwable error) {
    if (isClosed) {
      LOGGER.trace("Skip re-opening job stream of type '{}' for worker '{}'", jobType, workerName);
      return;
    }

    if (error != null) {
      logStreamError(error);
      retryDelay = backoffSupplier.supplyRetryDelay(retryDelay);
      LOGGER
          .atDebug()
          .addArgument(jobType)
          .addArgument(workerName)
          .addArgument(() -> Duration.ofMillis(retryDelay))
          .setMessage("Recreating closed stream of type '{}' and worker '{}' in {}")
          .log();
      executor.schedule(() -> open(command), retryDelay, TimeUnit.MILLISECONDS);
    }
  }

  private void logStreamError(final Throwable error) {
    final String errorMsg = "Failed to stream jobs of type '{}' to worker '{}'";
    if (error instanceof StatusRuntimeException) {
      final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) error;
      if (statusRuntimeException.getStatus().getCode() == Status.RESOURCE_EXHAUSTED.getCode()) {
        LOGGER.trace(errorMsg, jobType, workerName, error);
        return;
      }
    }

    LOGGER.warn(errorMsg, jobType, workerName, error);
  }
}
