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
package io.camunda.client.impl.worker;

import io.camunda.client.api.command.ActivateJobsCommandStep1.ActivateJobsCommandStep3;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.Loggers;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.slf4j.Logger;

public final class JobPollerImpl implements JobPoller {

  private static final Logger LOG = Loggers.JOB_POLLER_LOGGER;

  private final JobClient jobClient;
  private final Duration requestTimeout;
  private final String jobType;
  private final String workerName;
  private final Duration timeout;
  private final List<String> fetchVariables;
  private final List<String> tenantIds;
  private final TenantFilter tenantFilter;

  private int maxJobsToActivate;

  private Consumer<ActivatedJob> jobConsumer;
  private IntConsumer doneCallback;
  private Consumer<Throwable> errorCallback;
  private int activatedJobs;
  private BooleanSupplier openSupplier;

  public JobPollerImpl(
      final JobClient jobClient,
      final Duration requestTimeout,
      final String jobType,
      final String workerName,
      final Duration timeout,
      final List<String> fetchVariables,
      final List<String> tenantIds,
      final int maxJobsToActivate,
      final TenantFilter tenantFilter) {
    this.requestTimeout = requestTimeout;
    this.jobClient = jobClient;
    this.jobType = jobType;
    this.workerName = workerName;
    this.timeout = timeout;
    this.fetchVariables = fetchVariables;
    this.tenantIds = tenantIds;
    this.maxJobsToActivate = maxJobsToActivate;
    this.TenantFilter = TenantFilter;
  }

  private void reset() {
    activatedJobs = 0;
  }

  /**
   * Poll for available jobs. Jobs returned by zeebe are activated.
   *
   * @param maxJobsToActivate maximum number of jobs to activate
   * @param jobConsumer consumes each activated job individually
   * @param doneCallback consumes the number of jobs activated
   * @param errorCallback consumes thrown error
   * @param openSupplier supplies whether the consumer is open
   */
  @Override
  public void poll(
      final int maxJobsToActivate,
      final Consumer<ActivatedJob> jobConsumer,
      final IntConsumer doneCallback,
      final Consumer<Throwable> errorCallback,
      final BooleanSupplier openSupplier) {
    reset();

    this.maxJobsToActivate = maxJobsToActivate;
    this.jobConsumer = jobConsumer;
    this.doneCallback = doneCallback;
    this.errorCallback = errorCallback;
    this.openSupplier = openSupplier;

    poll();
  }

  private void poll() {
    LOG.trace(
        "Polling at max {} jobs for worker {} and job type {}",
        maxJobsToActivate,
        workerName,
        jobType);
    final ActivateJobsCommandStep3 activateCommand =
        jobClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(maxJobsToActivate)
            .timeout(timeout)
            .workerName(workerName)
            .TenantFilter(TenantFilter);
    if (TenantFilter == TenantFilter.PROVIDED) {
      activateCommand.tenantIds(tenantIds);
    }
    if (fetchVariables != null) {
      activateCommand.fetchVariables(fetchVariables);
    }
    activateCommand
        .requestTimeout(requestTimeout)
        .send()
        .exceptionally(
            throwable -> {
              if (openSupplier.getAsBoolean()) {
                try {
                  logFailure(throwable);
                } finally {
                  errorCallback.accept(throwable);
                }
              }
              return null;
            })
        .thenApply(
            activateJobsResponse -> {
              final List<ActivatedJob> jobs = activateJobsResponse.getJobs();
              activatedJobs += jobs.size();
              jobs.forEach(jobConsumer);
              if (activatedJobs > 0) {
                LOG.debug(
                    "Activated {} jobs for worker {} and job type {}",
                    activatedJobs,
                    workerName,
                    jobType);
              } else {
                LOG.trace("No jobs activated for worker {} and job type {}", workerName, jobType);
              }
              doneCallback.accept(activatedJobs);
              return null;
            });
  }

  private void logFailure(final Throwable throwable) {
    final String errorMsg = "Failed to activate jobs for worker {} and job type {}";

    if (throwable instanceof StatusRuntimeException) {
      final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
      if (statusRuntimeException.getStatus().getCode() == Status.RESOURCE_EXHAUSTED.getCode()) {
        // Log RESOURCE_EXHAUSTED status exceptions only as trace, otherwise it is just too
        // noisy. Furthermore it is not worth to be a warning since it is expected on a fully
        // loaded cluster. It should be handled by our backoff mechanism, but if there is an
        // issue or a configuration mistake the user can turn on trace logging to see this.
        LOG.trace(errorMsg, workerName, jobType, throwable);
        return;
      }
    }

    LOG.warn(errorMsg, workerName, jobType, throwable);
  }
}
