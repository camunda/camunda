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

import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.impl.Loggers;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.client.impl.subscription.*;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.sched.future.ActorFuture;
import org.slf4j.Logger;

public class JobSubscriber extends Subscriber {
  private static final Logger LOGGER = Loggers.TASK_SUBSCRIPTION_LOGGER;

  private final ZeebeClientImpl client;
  private final JobClient jobClient;
  private final JobSubscriptionSpec subscription;

  public JobSubscriber(
      ZeebeClientImpl client,
      JobSubscriptionSpec subscription,
      long subscriberKey,
      RemoteAddress eventSource,
      int partition,
      SubscriberGroup<JobSubscriber> group,
      SubscriptionManager acquisition) {
    super(subscriberKey, partition, subscription.getCapacity(), eventSource, group, acquisition);
    this.client = client;
    this.jobClient = client.topicClient(subscription.getTopic()).jobClient();
    this.subscription = subscription;
  }

  public int pollEvents(JobHandler jobHandler) {
    final int polledEvents =
        pollEvents(
            (e) -> {
              final JobEventImpl jobEvent = e.asRecordType(JobEventImpl.class);
              jobEvent.updateMetadata(e.getMetadata());

              try {
                jobHandler.handle(jobClient, jobEvent);
              } catch (Exception handlingException) {
                LOGGER.info(
                    "An error occurred when handling job "
                        + jobEvent.getMetadata().getKey()
                        + ". Reporting failure to broker.",
                    handlingException);
                try {
                  jobClient.newFailCommand(jobEvent).retries(jobEvent.getRetries() - 1).send();
                } catch (Exception failureException) {
                  LOGGER.info(
                      "Could not report failure of job "
                          + jobEvent.getMetadata().getKey()
                          + " to broker. Continuing with next job",
                      failureException);
                }
              }
            });

    return polledEvents;
  }

  @Override
  protected ActorFuture<?> requestEventSourceReplenishment(int eventsProcessed) {
    return new IncreaseJobSubscriptionCreditsCmdImpl(client.getCommandManager(), partitionId)
        .subscriberKey(subscriberKey)
        .credits(eventsProcessed)
        .send();
  }

  @Override
  public ActorFuture<Void> requestSubscriptionClose() {
    return new CloseJobSubscriptionCommandImpl(
            client.getCommandManager(), partitionId, subscriberKey)
        .send();
  }

  @Override
  public String toString() {
    return "JobSubscriber[topic="
        + subscription.getTopic()
        + ", partition="
        + partitionId
        + ", jobType="
        + subscription.getJobType()
        + ", subscriberKey="
        + subscriberKey
        + "]";
  }

  @Override
  public String getTopicName() {
    return subscription.getTopic();
  }
}
