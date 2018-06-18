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

import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.subscription.*;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

public class JobSubscriberGroup extends SubscriberGroup<JobSubscriber> implements JobWorker {
  private final JobSubscriptionSpec subscription;

  public JobSubscriberGroup(
      ActorControl actor,
      ZeebeClientImpl client,
      SubscriptionManager acquisition,
      JobSubscriptionSpec subscription) {
    super(actor, client, acquisition, subscription.getTopic());
    this.subscription = subscription;
  }

  @Override
  public int poll() {
    return poll(subscription.getJobHandler());
  }

  public int poll(JobHandler jobHandler) {
    int workCount = 0;
    for (JobSubscriber subscriber : subscribersList) {
      workCount += subscriber.pollEvents(jobHandler);
    }

    return workCount;
  }

  @Override
  protected ActorFuture<? extends EventSubscriptionCreationResult> requestNewSubscriber(
      int partitionId) {
    return new CreateJobSubscriptionCommandImpl(client.getCommandManager(), partitionId)
        .jobType(subscription.getJobType())
        .timeout(subscription.getTimeout())
        .worker(subscription.getWorker())
        .initialCredits(subscription.getCapacity())
        .send();
  }

  @Override
  protected JobSubscriber buildSubscriber(EventSubscriptionCreationResult result) {
    return new JobSubscriber(
        client,
        subscription,
        result.getSubscriberKey(),
        result.getEventPublisher(),
        result.getPartitionId(),
        this,
        subscriptionManager);
  }

  @Override
  protected String describeGroup() {
    return subscription.toString();
  }
}
