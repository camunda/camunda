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

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureGreaterThan;
import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;
import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNullNorEmpty;
import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensurePositive;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public final class JobWorkerBuilderImpl
    implements JobWorkerBuilderStep1, JobWorkerBuilderStep2, JobWorkerBuilderStep3 {

  public static final BackoffSupplier DEFAULT_BACKOFF_SUPPLIER =
      BackoffSupplier.newBackoffBuilder().build();
  public static final Duration DEFAULT_STREAMING_TIMEOUT = Duration.ofHours(8);
  private final JobClient jobClient;
  private final ScheduledExecutorService executorService;
  private final List<Closeable> closeables;
  private String jobType;
  private JobHandler handler;
  private Duration timeout;
  private String workerName;
  private int maxJobsActive;
  private Duration pollInterval;
  private Duration requestTimeout;
  private List<String> fetchVariables;
  private final List<String> defaultTenantIds;
  private final List<String> customTenantIds;
  private BackoffSupplier backoffSupplier;
  private boolean enableStreaming;
  private Duration streamingTimeout;
  private JobWorkerMetrics metrics = JobWorkerMetrics.noop();

  public JobWorkerBuilderImpl(
      final ZeebeClientConfiguration configuration,
      final JobClient jobClient,
      final ScheduledExecutorService executorService,
      final List<Closeable> closeables) {
    this.jobClient = jobClient;
    this.executorService = executorService;
    this.closeables = closeables;

    timeout = configuration.getDefaultJobTimeout();
    workerName = configuration.getDefaultJobWorkerName();
    maxJobsActive = configuration.getDefaultJobWorkerMaxJobsActive();
    pollInterval = configuration.getDefaultJobPollInterval();
    requestTimeout = configuration.getDefaultRequestTimeout();
    enableStreaming = configuration.getDefaultJobWorkerStreamEnabled();
    defaultTenantIds = configuration.getDefaultJobWorkerTenantIds();
    customTenantIds = new ArrayList<>();
    backoffSupplier = DEFAULT_BACKOFF_SUPPLIER;
    streamingTimeout = DEFAULT_STREAMING_TIMEOUT;
  }

  @Override
  public JobWorkerBuilderStep2 jobType(final String type) {
    jobType = type;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 handler(final JobHandler handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 timeout(final long timeout) {
    return timeout(Duration.ofMillis(timeout));
  }

  @Override
  public JobWorkerBuilderStep3 timeout(final Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 name(final String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 maxJobsActive(final int maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 pollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 fetchVariables(final List<String> fetchVariables) {
    this.fetchVariables = fetchVariables;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public JobWorkerBuilderStep3 backoffSupplier(final BackoffSupplier backoffSupplier) {
    this.backoffSupplier = backoffSupplier;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 streamEnabled(final boolean isStreamEnabled) {
    enableStreaming = isStreamEnabled;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 streamTimeout(final Duration timeout) {
    streamingTimeout = timeout;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 metrics(final JobWorkerMetrics metrics) {
    this.metrics = metrics == null ? JobWorkerMetrics.noop() : metrics;
    return this;
  }

  @Override
  public JobWorker open() {
    ensureNotNullNorEmpty("jobType", jobType);
    ensureNotNull("jobHandler", handler);
    ensurePositive("timeout", timeout);
    ensureNotNullNorEmpty("workerName", workerName);
    ensureGreaterThan("maxJobsActive", maxJobsActive, 0);

    final JobStreamer jobStreamer;
    final JobRunnableFactory jobRunnableFactory = new JobRunnableFactoryImpl(jobClient, handler);
    final JobPoller jobPoller =
        new JobPollerImpl(
            jobClient,
            requestTimeout,
            jobType,
            workerName,
            timeout,
            fetchVariables,
            getTenantIds(),
            maxJobsActive);

    final Executor jobExecutor;
    if (enableStreaming) {
      if (streamingTimeout != null) {
        ensurePositive("streamingTimeout", streamingTimeout);
      }

      jobStreamer =
          new JobStreamerImpl(
              jobClient,
              jobType,
              workerName,
              timeout,
              fetchVariables,
              getTenantIds(),
              streamingTimeout,
              backoffSupplier,
              executorService);
      jobExecutor = new BlockingExecutor(executorService, maxJobsActive, timeout);
    } else {
      jobStreamer = JobStreamer.noop();
      jobExecutor = executorService;
    }

    final JobWorkerImpl jobWorker =
        new JobWorkerImpl(
            maxJobsActive,
            executorService,
            pollInterval,
            jobRunnableFactory,
            jobPoller,
            jobStreamer,
            backoffSupplier,
            metrics,
            jobExecutor);
    closeables.add(jobWorker);
    return jobWorker;
  }

  @Override
  public JobWorkerBuilderStep3 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 tenantIds(final List<String> tenantIds) {
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 tenantIds(final String... tenantIds) {
    tenantIds(Arrays.asList(tenantIds));
    return this;
  }

  private List<String> getTenantIds() {
    return customTenantIds.isEmpty() ? defaultTenantIds : customTenantIds;
  }
}
