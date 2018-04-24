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
package io.zeebe.client.api.clients;

import io.zeebe.client.api.subscription.JobSubscriptionBuilderStep1;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;

/**
 * A client with access to all subscription-related operations:
 * <li>open a topic subscription (e.g. for event processing)
 * <li>open a job subscription (i.e. to work on jobs)
 */
public interface SubscriptionClient
{

    /**
     * Open a new subscription to process all events and command on the topic.
     * <p>
     * When the subscription is open then the broker publish all available events
     * and commands to the client. The client delegates the events/commands to the given
     * handlers and send an acknowledgement to the broker. When a subscription with the same
     * name is (re-)opened then the broker resumes the subscription at the last acknowledged
     * position and starts publishing with the next event/command.
     * <p>
     * This is a <i>managed</i> subscription. The given handlers are
     * automatically invoked whenever new event/command is available. Choose a
     * managed subscription when the client should invoke the handler in its
     * thread pool.
     *
     * <pre>
     * TopicSubscription subscription = subscriptionClient
     *  .newTopicSubscription()
     *  .name("my-app")
     *  .workflowInstanceEventHandler(wfEventHandler)
     *  .open();
     *
     * ...
     * subscription.close();
     * </pre>
     *
     * It is guaranteed that the handlers receive the events/commands of one partition in the same
     * order as they are occurred. For example: for a given workflow instance, a handler will
     * always see the CREATED before the COMPLETED event. This guarantee is not given for
     * events/commands from different partitions.
     *
     * @return a builder for the subscription
     */
    TopicSubscriptionBuilderStep1 newTopicSubscription();

    /**
     * Open a new subscription to work on jobs of a given type.
     * <p>
     * When the subscription is open then the broker assign available jobs
     * for this subscription and publish them to the client. The given handler
     * works on the jobs and complete them.
     * <p>
     * This is a <i>managed</i> subscription. The given job handler is
     * automatically invoked whenever new job is available. Choose a managed
     * subscription when the client should invoke the handler in its thread pool.
     *
     * <pre>
     * JobSubscription subscription = subscriptionClient
     *  .newJobSubscription()
     *  .jobType("payment")
     *  .handler(paymentHandler)
     *  .open();
     *
     * ...
     * subscription.close();
     * </pre>
     *
     * Example JobHandler implementation:
     * <pre>
     * public class PaymentHandler implements JobHandler
     * {
     *   &#64;Override
     *   public void handle(JobClient client, JobEvent jobEvent)
     *   {
     *     String json = jobEvent.getPayload();
     *     // modify payload
     *
     *     client
     *      .newCompleteCommand()
     *      .event(jobEvent)
     *      .payload(json)
     *      .send();
     *   }
     * };
     * </pre>
     *
     *
     * @return a builder for the subscription
     */
    JobSubscriptionBuilderStep1 newJobSubscription();

}
