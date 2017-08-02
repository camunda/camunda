/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.data;

public enum WorkflowInstanceState
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
