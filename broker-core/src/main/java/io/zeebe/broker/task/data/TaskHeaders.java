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
package io.zeebe.broker.task.data;

import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.*;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TaskHeaders extends UnpackedObject
{
    private static final String EMPTY_STRING = "";
    private static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    private final LongProperty workflowInstanceKeyProp = new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, EMPTY_STRING);
    private final IntegerProperty workflowDefinitionVersionProp = new IntegerProperty("workflowDefinitionVersion", -1);
    private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1L);
    private final StringProperty activityIdProp = new StringProperty(PROP_WORKFLOW_ACTIVITY_ID, EMPTY_STRING);
    private final LongProperty activityInstanceKeyProp = new LongProperty("activityInstanceKey", -1L);

    public TaskHeaders()
    {
        this.declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowDefinitionVersionProp)
            .declareProperty(workflowKeyProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(activityInstanceKeyProp);
    }

    public long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public TaskHeaders setWorkflowInstanceKey(long key)
    {
        this.workflowInstanceKeyProp.setValue(key);
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public TaskHeaders setActivityId(DirectBuffer activityId)
    {
        return setActivityId(activityId, 0, activityId.capacity());
    }

    public TaskHeaders setActivityId(DirectBuffer activityId, int offset, int length)
    {
        this.activityIdProp.setValue(activityId, offset, length);
        return this;
    }

    public TaskHeaders setBpmnProcessId(DirectBuffer bpmnProcessId)
    {
        this.bpmnProcessIdProp.setValue(bpmnProcessId);
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public int getWorkflowDefinitionVersion()
    {
        return workflowDefinitionVersionProp.getValue();
    }

    public TaskHeaders setWorkflowDefinitionVersion(int version)
    {
        this.workflowDefinitionVersionProp.setValue(version);
        return this;
    }

    public long getActivityInstanceKey()
    {
        return activityInstanceKeyProp.getValue();
    }

    public TaskHeaders setActivityInstanceKey(long activityInstanceKey)
    {
        this.activityInstanceKeyProp.setValue(activityInstanceKey);
        return this;
    }

    public long getWorkflowKey()
    {
        return workflowKeyProp.getValue();
    }

    public TaskHeaders setWorkflowKey(long workflowKey)
    {
        this.workflowKeyProp.setValue(workflowKey);
        return this;
    }

}
