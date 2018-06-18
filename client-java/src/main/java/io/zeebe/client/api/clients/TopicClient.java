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

import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;

/** A client to operate on workflows, jobs and subscriptions. */
public interface TopicClient {
  /**
   * A client to
   * <li>deploy a workflow
   * <li>create a workflow instance
   * <li>cancel a workflow instance
   * <li>update the payload of a workflow instance
   * <li>request a workflow resource
   * <li>request all deployed workflows
   *
   * @return a client with access to all workflow-related operations.
   */
  WorkflowClient workflowClient();

  /**
   * A client to
   * <li>create a (standalone) job
   * <li>complete a job
   * <li>mark a job as failed
   * <li>update the retries of a job
   *
   * @return a client with access to all job-related operations.
   */
  JobClient jobClient();

  /**
   * Open a new subscription to receive all records (events and commands) of this topic.
   *
   * <p>While the subscription is open, the broker continuously publishes records to the client. The
   * client delegates the events/commands to the provided handlers. The client periodically
   * acknowledges that records have been received and handled. When a subscription with the same
   * name is (re-)opened, then the broker resumes the subscription from the last acknowledged record
   * and starts publishing at the next event/command.
   *
   * <pre>
   * TopicSubscription subscription = zeebeClient
   *  .topicClient()
   *  .newSubscription()
   *  .name("my-app")
   *  .workflowInstanceEventHandler(wfEventHandler)
   *  .open();
   *
   * ...
   * subscription.close();
   * </pre>
   *
   * Per partition it is guaranteed that handlers are called per record in the order of occurrence.
   * For example: for a given workflow instance, a handler will always receive the CREATED event
   * before the COMPLETED event. Records from different partitions are handled sequentially, but in
   * arbitrary order.
   *
   * @return a builder for the subscription
   */
  TopicSubscriptionBuilderStep1 newSubscription();
}
