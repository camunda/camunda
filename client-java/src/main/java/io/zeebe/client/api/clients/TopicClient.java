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

/**
 * A client to operate on workflows, jobs and subscriptions.
 */
public interface TopicClient
{
    /**
     * A client to
     * <li>deploy a workflow
     * <li>create a workflow instance
     * <li>cancel a workflow instance
     * <li>update the payload of a workflow instance
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
     * A client to
     * <li>open a topic subscription (e.g. for event processing)
     * <li>open a job subscription (i.e. to work on jobs)
     *
     * @return a client with access to all subscription-related operations.
     */
    SubscriptionClient subscriptionClient();

}
