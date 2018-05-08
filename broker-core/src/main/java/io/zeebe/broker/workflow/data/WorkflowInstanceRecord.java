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

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import io.zeebe.msgpack.spec.MsgPackHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class WorkflowInstanceRecord extends UnpackedObject
{
    public static final DirectBuffer NO_PAYLOAD = new UnsafeBuffer(MsgPackHelper.NIL);

    public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
    public static final String PROP_WORKFLOW_ACTIVITY_ID = "activityId";
    public static final String PROP_WORKFLOW_VERSION = "version";
    public static final String PROP_WORKFLOW_KEY = "workflowKey";
    public static final String PROP_WORKFLOW_PAYLOAD = "payload";

    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, "");
    private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION, -1);
    private final LongProperty workflowKeyProp = new LongProperty(PROP_WORKFLOW_KEY, -1L);

    private final LongProperty workflowInstanceKeyProp = new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
    private final StringProperty activityIdProp = new StringProperty(PROP_WORKFLOW_ACTIVITY_ID, "");

    private final BinaryProperty payloadProp = new BinaryProperty(PROP_WORKFLOW_PAYLOAD, NO_PAYLOAD);

    public WorkflowInstanceRecord()
    {
        this
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(versionProp)
            .declareProperty(workflowKeyProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(payloadProp);
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public WorkflowInstanceRecord setBpmnProcessId(DirectBuffer directBuffer)
    {
        bpmnProcessIdProp.setValue(directBuffer);
        return this;
    }

    public WorkflowInstanceRecord setBpmnProcessId(DirectBuffer directBuffer, int offset, int length)
    {
        bpmnProcessIdProp.setValue(directBuffer, offset, length);
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public WorkflowInstanceRecord setActivityId(String activityId)
    {
        this.activityIdProp.setValue(activityId);
        return this;
    }

    public WorkflowInstanceRecord setActivityId(DirectBuffer activityId)
    {
        return setActivityId(activityId, 0, activityId.capacity());
    }

    public WorkflowInstanceRecord setActivityId(DirectBuffer activityId, int offset, int length)
    {
        this.activityIdProp.setValue(activityId, offset, length);
        return this;
    }

    public Long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public WorkflowInstanceRecord setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
        return this;
    }

    public int getVersion()
    {
        return versionProp.getValue();
    }

    public WorkflowInstanceRecord setVersion(int version)
    {
        this.versionProp.setValue(version);
        return this;
    }

    public long getWorkflowKey()
    {
        return workflowKeyProp.getValue();
    }

    public WorkflowInstanceRecord setWorkflowKey(long workflowKey)
    {
        this.workflowKeyProp.setValue(workflowKey);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    public WorkflowInstanceRecord setPayload(DirectBuffer payload)
    {
        payloadProp.setValue(payload);
        return this;
    }

    public WorkflowInstanceRecord setPayload(DirectBuffer payload, int offset, int length)
    {
        payloadProp.setValue(payload, offset, length);
        return this;
    }

}
