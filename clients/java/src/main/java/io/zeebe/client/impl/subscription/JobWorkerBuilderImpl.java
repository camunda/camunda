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
package io.zeebe.client.impl.subscription;

import static io.zeebe.client.impl.ArgumentUtil.ensureGreaterThan;
import static io.zeebe.client.impl.ArgumentUtil.ensureNotNull;
import static io.zeebe.client.impl.ArgumentUtil.ensureNotNullNorEmpty;

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest.Builder;
import io.zeebe.util.CloseableSilently;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class JobWorkerBuilderImpl
    implements JobWorkerBuilderStep1, JobWorkerBuilderStep2, JobWorkerBuilderStep3 {

  private final GatewayStub gatewayStub;
  private final JobClient jobClient;
  private final ZeebeObjectMapper objectMapper;
  private final ScheduledExecutorService executorService;
  private final List<CloseableSilently> closeables;

  private String jobType;
  private JobHandler handler;
  private long timeout;
  private String workerName;
  private int maxJobsActive;
  private Duration pollInterval;
  private List<String> fetchVariables;

  public JobWorkerBuilderImpl(
      ZeebeClientConfiguration configuration,
      GatewayStub gatewayStub,
      JobClient jobClient,
      ZeebeObjectMapper objectMapper,
      ScheduledExecutorService executorService,
      List<CloseableSilently> closeables) {
    this.gatewayStub = gatewayStub;
    this.jobClient = jobClient;
    this.objectMapper = objectMapper;
    this.executorService = executorService;
    this.closeables = closeables;

    this.timeout = configuration.getDefaultJobTimeout().toMillis();
    this.workerName = configuration.getDefaultJobWorkerName();
    this.maxJobsActive = configuration.getDefaultJobWorkerMaxJobsActive();
    this.pollInterval = configuration.getDefaultJobPollInterval();
  }

  @Override
  public JobWorkerBuilderStep2 jobType(String type) {
    this.jobType = type;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 handler(JobHandler handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 timeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 timeout(Duration timeout) {
    return timeout(timeout.toMillis());
  }

  @Override
  public JobWorkerBuilderStep3 name(String workerName) {
    this.workerName = workerName;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 maxJobsActive(int maxJobsActive) {
    this.maxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 pollInterval(Duration pollInterval) {
    this.pollInterval = pollInterval;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 fetchVariables(List<String> fetchVariables) {
    this.fetchVariables = fetchVariables;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 fetchVariables(String... fetchVariables) {
    return fetchVariables(Arrays.asList(fetchVariables));
  }

  @Override
  public JobWorker open() {
    ensureNotNullNorEmpty("jobType", jobType);
    ensureNotNull("jobHandler", handler);
    ensureGreaterThan("timeout", timeout, 0L);
    ensureNotNullNorEmpty("workerName", workerName);
    ensureGreaterThan("maxJobsActive", maxJobsActive, 0);

    final Builder requestBuilder =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setTimeout(timeout)
            .setWorker(workerName)
            .setMaxJobsToActivate(maxJobsActive);

    if (fetchVariables != null) {
      requestBuilder.addAllFetchVariable(fetchVariables);
    }

    final JobRunnableFactory jobRunnableFactory = new JobRunnableFactory(jobClient, handler);
    final JobPoller jobPoller = new JobPoller(gatewayStub, requestBuilder, objectMapper);

    final JobWorkerImpl jobWorker =
        new JobWorkerImpl(
            maxJobsActive, executorService, pollInterval, jobRunnableFactory, jobPoller);
    closeables.add(jobWorker);
    return jobWorker;
  }
}
