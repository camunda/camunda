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
import org.agrona.DirectBuffer;

public class WorkflowEvent extends UnpackedObject
{
    private final EnumProperty<WorkflowState> stateProp = new EnumProperty<>("state", WorkflowState.class);

    private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
    private final IntegerProperty versionProp = new IntegerProperty("version");
    private final BinaryProperty bpmnXmlProp = new BinaryProperty("bpmnXml");

    private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey");

    public WorkflowEvent()
    {
        this.declareProperty(stateProp)
            .declareProperty(bpmnProcessIdProp)
            .declareProperty(versionProp)
            .declareProperty(bpmnXmlProp)
            .declareProperty(deploymentKeyProp);
    }

    public WorkflowState getState()
    {
        return stateProp.getValue();
    }

    public WorkflowEvent setState(WorkflowState eventType)
    {
        this.stateProp.setValue(eventType);
        return this;
    }

    public DirectBuffer getBpmnProcessId()
    {
        return bpmnProcessIdProp.getValue();
    }

    public WorkflowEvent setBpmnProcessId(DirectBuffer bpmnProcessId)
    {
        return setBpmnProcessId(bpmnProcessId, 0, bpmnProcessId.capacity());
    }

    public WorkflowEvent setBpmnProcessId(DirectBuffer bpmnProcessId, int offset, int length)
    {
        this.bpmnProcessIdProp.setValue(bpmnProcessId, offset, length);
        return this;
    }

    public int getVersion()
    {
        return versionProp.getValue();
    }

    public WorkflowEvent setVersion(int version)
    {
        this.versionProp.setValue(version);
        return this;
    }

    public DirectBuffer getBpmnXml()
    {
        return bpmnXmlProp.getValue();
    }

    public WorkflowEvent setBpmnXml(DirectBuffer bpmnXml)
    {
        return setBpmnXml(bpmnXml, 0, bpmnXml.capacity());
    }

    public WorkflowEvent setBpmnXml(DirectBuffer bpmnXml, int offset, int length)
    {
        this.bpmnXmlProp.setValue(bpmnXml, offset, length);
        return this;
    }

    public long getDeploymentKey()
    {
        return deploymentKeyProp.getValue();
    }

    public WorkflowEvent setDeploymentKey(long deploymentKey)
    {
        this.deploymentKeyProp.setValue(deploymentKey);
        return this;
    }

}
