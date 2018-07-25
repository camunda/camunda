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
package io.zeebe.client.impl.subscription.job;

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.zeebe.client.api.subscription.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.client.impl.subscription.SubscriptionManager;
import io.zeebe.util.EnsureUtil;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class JobSubcriptionBuilder
    implements JobWorkerBuilderStep1, JobWorkerBuilderStep2, JobWorkerBuilderStep3 {
  private final SubscriptionManager subscriptionManager;
  private final String topic;

  private String jobType;
  private long timeout = -1L;
  private String worker;
  private JobHandler jobHandler;
  private int bufferSize;

  public JobSubcriptionBuilder(TopicClientImpl client) {
    this.topic = client.getTopic();
    this.subscriptionManager = client.getSubscriptionManager();

    // apply defaults from configuration
    final ZeebeClientConfiguration configuration = client.getConfiguration();
    this.worker = configuration.getDefaultJobWorkerName();
    this.timeout = configuration.getDefaultJobTimeout().toMillis();
    this.bufferSize = configuration.getDefaultJobSubscriptionBufferSize();
  }

  @Override
  public JobWorkerBuilderStep2 jobType(String jobType) {
    this.jobType = jobType;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 timeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 timeout(Duration timeout) {
    this.timeout = timeout.toMillis();
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 name(String name) {
    this.worker = name;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 bufferSize(int bufferSize) {
    this.bufferSize = bufferSize;
    return this;
  }

  @Override
  public JobWorkerBuilderStep3 handler(JobHandler handler) {
    EnsureUtil.ensureNotNull("handler", handler);
    this.jobHandler = handler;
    return this;
  }

  @Override
  public JobWorker open() {
    EnsureUtil.ensureNotNullOrEmpty("jobType", jobType);
    EnsureUtil.ensureGreaterThan("timeout", timeout, 0L);
    EnsureUtil.ensureNotNullOrEmpty("worker", worker);
    EnsureUtil.ensureGreaterThan("jobFetchSize", bufferSize, 0);

    final JobSubscriptionSpec subscription =
        new JobSubscriptionSpec(topic, jobHandler, jobType, timeout, worker, bufferSize);

    final Future<JobSubscriberGroup> group = subscriptionManager.openJobSubscription(subscription);

    try {
      return group.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ClientException("Could not open subscription", e);
    }
  }
}
