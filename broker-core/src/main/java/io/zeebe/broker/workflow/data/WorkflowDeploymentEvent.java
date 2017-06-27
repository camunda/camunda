package io.zeebe.broker.workflow.data;

import static io.zeebe.broker.workflow.data.WorkflowInstanceEvent.PROP_EVENT_TYPE;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.broker.util.msgpack.UnpackedObject;
import io.zeebe.broker.util.msgpack.property.ArrayProperty;
import io.zeebe.broker.util.msgpack.property.EnumProperty;
import io.zeebe.broker.util.msgpack.property.StringProperty;
import io.zeebe.broker.util.msgpack.value.ArrayValue;
import io.zeebe.broker.util.msgpack.value.ArrayValueIterator;
import io.zeebe.msgpack.spec.MsgPackHelper;

public class WorkflowDeploymentEvent extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    private final EnumProperty<WorkflowDeploymentEventType> eventTypeProp = new EnumProperty<>(PROP_EVENT_TYPE, WorkflowDeploymentEventType.class);
    private final StringProperty bpmnXmlProp = new StringProperty("bpmnXml");

    private final ArrayProperty<DeployedWorkflow> deployedWorkflowsProp = new ArrayProperty<>(
            "deployedWorkflows",
            new ArrayValue<>(),
            new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
            new DeployedWorkflow());

    private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

    public WorkflowDeploymentEvent()
    {
        this.declareProperty(eventTypeProp)
            .declareProperty(bpmnXmlProp)
            .declareProperty(deployedWorkflowsProp)
            .declareProperty(errorMessageProp);
    }

    public WorkflowDeploymentEventType getEventType()
    {
        return eventTypeProp.getValue();
    }

    public WorkflowDeploymentEvent setEventType(WorkflowDeploymentEventType event)
    {
        this.eventTypeProp.setValue(event);
        return this;
    }

    public DirectBuffer getBpmnXml()
    {
        return bpmnXmlProp.getValue();
    }

    public WorkflowDeploymentEvent setBpmnXml(DirectBuffer bpmnXml)
    {
        return setBpmnXml(bpmnXml, 0, bpmnXml.capacity());
    }

    public WorkflowDeploymentEvent setBpmnXml(DirectBuffer bpmnXml, int offset, int length)
    {
        this.bpmnXmlProp.setValue(bpmnXml, offset, length);
        return this;
    }

    public ArrayValueIterator<DeployedWorkflow> deployedWorkflows()
    {
        return deployedWorkflowsProp;
    }

    public DirectBuffer getErrorMessage()
    {
        return errorMessageProp.getValue();
    }

    public WorkflowDeploymentEvent setErrorMessage(String errorMessage)
    {
        this.errorMessageProp.setValue(errorMessage);
        return this;
    }

    public WorkflowDeploymentEvent setErrorMessage(DirectBuffer errorMessage, int offset, int length)
    {
        this.errorMessageProp.setValue(errorMessage, offset, length);
        return this;
    }
}
