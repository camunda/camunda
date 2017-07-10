/**
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
