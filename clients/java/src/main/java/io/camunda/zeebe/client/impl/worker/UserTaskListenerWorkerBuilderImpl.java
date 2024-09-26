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
import io.camunda.zeebe.client.api.usertask.UserTaskListenerHandler;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerJob;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerWorkerBuilderStep1;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerWorkerBuilderStep1.UserTaskListenerBuilderStep2;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerWorkerBuilderStep1.UserTaskListenerBuilderStep3;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerWorkerBuilderStep1.UserTaskListenerBuilderStep4;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerMetrics;
import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public class UserTaskListenerWorkerBuilderImpl
    implements UserTaskListenerWorkerBuilderStep1,
        UserTaskListenerBuilderStep2,
        UserTaskListenerBuilderStep3,
        UserTaskListenerBuilderStep4 {

  public static final BackoffSupplier DEFAULT_BACKOFF_SUPPLIER =
      BackoffSupplier.newBackoffBuilder().build();
  public static final Duration DEFAULT_STREAMING_TIMEOUT = Duration.ofHours(8);
  private final JobClient jobClient;
  private final ScheduledExecutorService executorService;
  private final List<Closeable> closeables;
  private String jobType;
  // TODO: we might want to use the enum in the future
  private String eventType;
  private JobHandler delegatedHandler;
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

  private UserTaskListenerHandler taskListener;

  public UserTaskListenerWorkerBuilderImpl(
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
  public UserTaskListenerBuilderStep2 jobType(final String type) {
    jobType = type;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep3 eventType(final String eventType) {
    this.eventType = eventType;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 taskListener(final UserTaskListenerHandler taskListener) {
    this.taskListener = taskListener;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 timeout(final long timeout) {
    return timeout(Duration.ofMillis(timeout));
  }

  @Override
  public UserTaskListenerBuilderStep4 timeout(final Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 name(final String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 maxJobsActive(final int maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 pollInterval(final Duration pollInterval) {
    this.pollInterval = pollInterval;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 fetchVariables(final List<String> fetchVariables) {
    this.fetchVariables = fetchVariables;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 fetchVariables(final String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public UserTaskListenerBuilderStep4 backoffSupplier(final BackoffSupplier backoffSupplier) {
    this.backoffSupplier = backoffSupplier;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 streamEnabled(final boolean isStreamEnabled) {
    enableStreaming = isStreamEnabled;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 streamTimeout(final Duration timeout) {
    streamingTimeout = timeout;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 metrics(final JobWorkerMetrics metrics) {
    this.metrics = metrics == null ? JobWorkerMetrics.noop() : metrics;
    return this;
  }

  @Override
  public JobWorker open() {
    // TODO : clarify what checks are needed
    ensureNotNullNorEmpty("jobType", jobType);
    ensureNotNull("taskListener", taskListener);
    ensurePositive("timeout", timeout);
    ensureNotNullNorEmpty("workerName", workerName);
    ensureGreaterThan("maxJobsActive", maxJobsActive, 0);

    delegatedHandler =
        (client, job) -> {
          // TODO : we should filter by eventType, such as: complete, start, end. Please check
          // JobRecord class.
          if (!job.getJobListenerEventType().equals(eventType)) {
            return;
          }

          // TODO: verify if protocol can be accessed from here in order to retrieve properties
          // TODO: keys, ex. by adding dependency on protocol module.

          // TODO: clarify how to get user task key.
          final long userTaskKey =
              Long.parseLong(job.getCustomHeaders().get("io.camunda.zeebe:taskKey"));

          final String assignee = job.getCustomHeaders().get("io.camunda.zeebe:assignee");
          final String candidateGroups =
              job.getCustomHeaders().get("io.camunda.zeebe:candidateGroups");
          final String candidateUsers =
              job.getCustomHeaders().get("io.camunda.zeebe:candidateUsers");
          final String dueDate = job.getCustomHeaders().get("io.camunda.zeebe:dueDate");
          final String followUpDate = job.getCustomHeaders().get("io.camunda.zeebe:followUpDate");
          final String formKey = job.getCustomHeaders().get("io.camunda.zeebe:formKey");

          // TODO:  add the remaining user task properties
          final UserTaskListenerJob listenerJob =
              new UserTaskListenerJob() {
                @Override
                public long getUserTaskKey() {
                  return userTaskKey;
                }

                @Override
                public String getAssignee() {
                  return assignee;
                }

                @Override
                public String getCandidateGroups() {
                  return candidateGroups;
                }

                @Override
                public String getCandidateUsers() {
                  return candidateUsers;
                }

                @Override
                public String getDueDate() {
                  return dueDate;
                }

                @Override
                public String getFollowUpDate() {
                  return followUpDate;
                }

                @Override
                public String getFormKey() {
                  return formKey;
                }
              };

          taskListener.handle(listenerJob);
        };

    final JobStreamer jobStreamer;
    final JobRunnableFactory jobRunnableFactory =
        new JobRunnableFactoryImpl(jobClient, delegatedHandler);
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
  public UserTaskListenerBuilderStep4 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 tenantIds(final List<String> tenantIds) {
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 tenantIds(final String... tenantIds) {
    tenantIds(Arrays.asList(tenantIds));
    return this;
  }

  private List<String> getTenantIds() {
    return customTenantIds.isEmpty() ? defaultTenantIds : customTenantIds;
  }
}
