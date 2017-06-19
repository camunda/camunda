package org.camunda.tngp.broker.workflow.data;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.BinaryProperty;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;
import org.camunda.tngp.msgpack.spec.MsgPackHelper;

public class WorkflowInstanceEvent extends UnpackedObject
{
    public static final DirectBuffer NO_PAYLOAD = new UnsafeBuffer(MsgPackHelper.NIL);

    public static final String PROP_EVENT_TYPE = "eventType";
    public static final String PROP_WORKFLOW_BPMN_PROCESS_ID = "bpmnProcessId";
    public static final String PROP_WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";
    public static final String PROP_WORKFLOW_ACTIVITY_ID = "activityId";
    public static final String PROP_WORKFLOW_VERSION = "version";
    public static final String PROP_WORKFLOW_PAYLOAD = "payload";

    private final EnumProperty<WorkflowInstanceEventType> eventTypeProp = new EnumProperty<>(PROP_EVENT_TYPE, WorkflowInstanceEventType.class);
    private final StringProperty bpmnProcessIdProp = new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, "");
    private final LongProperty workflowInstanceKeyProp = new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
    private final StringProperty activityIdProp = new StringProperty(PROP_WORKFLOW_ACTIVITY_ID, "");
    private final IntegerProperty versionProp = new IntegerProperty(PROP_WORKFLOW_VERSION, -1);
    private final BinaryProperty payloadProp = new BinaryProperty(PROP_WORKFLOW_PAYLOAD, NO_PAYLOAD);

    public WorkflowInstanceEvent()
    {
        this
            .declareProperty(eventTypeProp)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(versionProp)
            .declareProperty(payloadProp);
    }

    public WorkflowInstanceEventType getEventType()
    {
        return eventTypeProp.getValue();
    }

    public WorkflowInstanceEvent setEventType(WorkflowInstanceEventType eventType)
    {
        this.eventTypeProp.setValue(eventType);
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public WorkflowInstanceEvent setBpmnProcessId(DirectBuffer directBuffer)
    {
        bpmnProcessIdProp.setValue(directBuffer);
        return this;
    }

    public WorkflowInstanceEvent setBpmnProcessId(DirectBuffer directBuffer, int offset, int length)
    {
        bpmnProcessIdProp.setValue(directBuffer, offset, length);
        return this;
    }

    public DirectBuffer getActivityId()
    {
        return activityIdProp.getValue();
    }

    public WorkflowInstanceEvent setActivityId(String activityId)
    {
        this.activityIdProp.setValue(activityId);
        return this;
    }

    public WorkflowInstanceEvent setActivityId(DirectBuffer activityId)
    {
        return setActivityId(activityId, 0, activityId.capacity());
    }

    public WorkflowInstanceEvent setActivityId(DirectBuffer activityId, int offset, int length)
    {
        this.activityIdProp.setValue(activityId, offset, length);
        return this;
    }

    public Long getWorkflowInstanceKey()
    {
        return workflowInstanceKeyProp.getValue();
    }

    public WorkflowInstanceEvent setWorkflowInstanceKey(long workflowInstanceKey)
    {
        this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
        return this;
    }

    public int getVersion()
    {
        return versionProp.getValue();
    }

    public WorkflowInstanceEvent setVersion(int version)
    {
        this.versionProp.setValue(version);
        return this;
    }

    public DirectBuffer getPayload()
    {
        return payloadProp.getValue();
    }

    public WorkflowInstanceEvent setPayload(DirectBuffer payload)
    {
        payloadProp.setValue(payload);
        return this;
    }

    public WorkflowInstanceEvent setPayload(DirectBuffer payload, int offset, int length)
    {
        payloadProp.setValue(payload, offset, length);
        return this;
    }

}
