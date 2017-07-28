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
package io.zeebe.client;

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.workflow.cmd.CreateDeploymentCommand;
import io.zeebe.client.workflow.cmd.CreateWorkflowInstanceCommand;
import io.zeebe.client.workflow.cmd.UpdatePayloadCommand;

/**
 * Provides access to APIs revolving around workflow events.
 */
public interface WorkflowsClient
{

    /**
     * Deploy new workflow definitions.
     *
     * @param topic the topic to deploy the definitions to
     */
    CreateDeploymentCommand deploy(String topic);

    /**
     * Create a new workflow instance.
     *
     * @param topic the topic to create the instance on
     */
    CreateWorkflowInstanceCommand create(String topic);

    /**
     * Cancel a workflow instance.
     *
     * @param baseEvent the workflow instance event to base the request on.
     */
    Request<WorkflowInstanceEvent> cancel(WorkflowInstanceEvent baseEvent);

    /**
     * Update the payload of a workflow instance.
     *
     * @param baseEvent the workflow instance event to base the request on. Must be
     *   an event of an activity instance that is currently in any of the following states:
     *   ACTIVITY_READY, ACTIVITY_ACTIVATED, ACTIVITY_COMPLETING
     */
    UpdatePayloadCommand updatePayload(WorkflowInstanceEvent baseEvent);
}
