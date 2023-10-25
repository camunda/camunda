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

import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensurePositive;

import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.usertask.UserTaskListener;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerBuilderStep1;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerBuilderStep1.UserTaskListenerBuilderStep2;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerBuilderStep1.UserTaskListenerBuilderStep3;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerBuilderStep1.UserTaskListenerBuilderStep4;
import io.camunda.zeebe.client.api.usertask.UserTaskListenerJob;
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
import java.util.concurrent.ScheduledExecutorService;

public final class UserTaskListenerBuilderImpl
    implements UserTaskListenerBuilderStep1,
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
  private JobHandler fakeHandler;
  private final Duration timeout;
  private final String workerName;
  private final int maxJobsActive;
  private final Duration pollInterval;
  private final Duration requestTimeout;
  private List<String> fetchVariables;
  private final List<String> defaultTenantIds;
  private final List<String> customTenantIds;
  private final BackoffSupplier backoffSupplier;
  private final boolean enableStreaming;
  private final Duration streamingTimeout;
  private final JobWorkerMetrics metrics = JobWorkerMetrics.noop();

  private String eventType = "";
  private String listenerName = "";

  private UserTaskListener listener;

  public UserTaskListenerBuilderImpl(
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
  public UserTaskListenerBuilderStep2 eventType(final String type) {
    eventType = type;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep3 listenerName(final String listenerName) {
    this.listenerName = listenerName;
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep4 handler(final UserTaskListener handler) {
    listener = handler;
    return this;
  }

  @Override
  public JobWorker open() {

    jobType = String.format("_userTaskListener_%s_%s", eventType, listenerName);

    fakeHandler =
        (client, job) -> {
          final long userTaskKey = job.getUserTaskKey();
          final String assignee = job.getCustomHeaders().get("io.camunda.zeebe:assignee");

          final UserTaskListenerJob userTask =
              new UserTaskListenerJob() {
                @Override
                public long getUserTaskKey() {
                  return userTaskKey;
                }

                @Override
                public String getAssignee() {
                  return assignee;
                }
              };

          listener.handle(userTask);
        };

    final JobStreamer jobStreamer;
    final JobRunnableFactory jobRunnableFactory =
        new JobRunnableFactoryImpl(jobClient, fakeHandler);
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
    } else {
      jobStreamer = JobStreamer.noop();
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
            metrics);
    closeables.add(jobWorker);
    return jobWorker;
  }

  @Override
  public UserTaskListenerBuilderStep3 tenantId(final String tenantId) {
    customTenantIds.add(tenantId);
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep3 tenantIds(final List<String> tenantIds) {
    customTenantIds.addAll(tenantIds);
    return this;
  }

  @Override
  public UserTaskListenerBuilderStep3 tenantIds(final String... tenantIds) {
    tenantIds(Arrays.asList(tenantIds));
    return this;
  }

  private List<String> getTenantIds() {
    return customTenantIds.isEmpty() ? defaultTenantIds : customTenantIds;
  }
}
