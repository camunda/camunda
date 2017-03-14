package org.camunda.tngp.broker.workflow.data;

import org.camunda.tngp.broker.util.msgpack.UnpackedObject;
import org.camunda.tngp.broker.util.msgpack.property.EnumProperty;
import org.camunda.tngp.broker.util.msgpack.property.StringProperty;

public class WorkflowDeploymentEvent extends UnpackedObject
{
    private final EnumProperty<WorkflowDeploymentEventType> eventProp = new EnumProperty<>("event", WorkflowDeploymentEventType.class);
    private final StringProperty bpmnXmlProp = new StringProperty("bpmnXml");

    public WorkflowDeploymentEvent()
    {
        this.declareProperty(eventProp)
            .declareProperty(bpmnXmlProp);
    }

    public WorkflowDeploymentEventType getEvent()
    {
        return eventProp.getValue();
    }
}
