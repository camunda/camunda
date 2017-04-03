package org.camunda.tngp.broker.workflow.data;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.IntegerProperty;
import org.camunda.tngp.broker.util.msgpack.property.LongProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class WorkflowInstanceEvent extends UnpackedObject
{
    private final EnumProperty<WorkflowInstanceEventType> eventTypeProp = new EnumProperty<>("eventType", WorkflowInstanceEventType.class);
    private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
    private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
    private final StringProperty activityIdProp = new StringProperty("activityId", "");
    private final IntegerProperty versionProp = new IntegerProperty("version", -1);

    public WorkflowInstanceEvent()
    {
        this
            .declareProperty(eventTypeProp)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(workflowInstanceKeyProp)
            .declareProperty(activityIdProp)
            .declareProperty(versionProp);
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
        bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
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

}
