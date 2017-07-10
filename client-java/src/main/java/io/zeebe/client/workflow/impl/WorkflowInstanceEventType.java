/**
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
package io.zeebe.client.workflow.impl;

/**
 * Represents the workflow instance event types,
 * which are written by the broker.
 */
public enum WorkflowInstanceEventType
{
    CREATE_WORKFLOW_INSTANCE,
    WORKFLOW_INSTANCE_CREATED,
    WORKFLOW_INSTANCE_REJECTED,

    START_EVENT_OCCURRED,
    END_EVENT_OCCURRED,

    SEQUENCE_FLOW_TAKEN,

    ACTIVITY_READY,
    ACTIVITY_ACTIVATED,
    ACTIVITY_COMPLETING,
    ACTIVITY_COMPLETED,
    ACTIVITY_TERMINATED,

    WORKFLOW_INSTANCE_COMPLETED,

    CANCEL_WORKFLOW_INSTANCE,
    WORKFLOW_INSTANCE_CANCELED,
    CANCEL_WORKFLOW_INSTANCE_REJECTED,

    UPDATE_PAYLOAD,
    PAYLOAD_UPDATED,
    UPDATE_PAYLOAD_REJECTED;
}

