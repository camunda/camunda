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
package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import javax.xml.bind.annotation.XmlAttribute;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import org.agrona.DirectBuffer;

public class SequenceFlowImpl extends FlowElementImpl implements SequenceFlow
{
    private DirectBuffer sourceRef;
    private DirectBuffer targetRef;

    private FlowNodeImpl sourceNode;
    private FlowNodeImpl targetNode;

    @XmlAttribute(name = BpmnConstants.BPMN_ELEMENT_SOURCE_REF)
    public void setSourceRef(String sourceRef)
    {
        this.sourceRef = wrapString(sourceRef);
    }

    public String getSourceRef()
    {
        return bufferAsString(sourceRef);
    }

    @XmlAttribute(name = BpmnConstants.BPMN_ELEMENT_TARGET_REF)
    public void setTargetRef(String targetRef)
    {
        this.targetRef = wrapString(targetRef);
    }

    public String getTargetRef()
    {
        return bufferAsString(targetRef);
    }

    public DirectBuffer getSourceRefAsBuffer()
    {
        return sourceRef;
    }

    public DirectBuffer getTargetRefAsBuffer()
    {
        return targetRef;
    }

    @Override
    public FlowNode getSourceNode()
    {
        return sourceNode;
    }

    public void setSourceNode(FlowNodeImpl sourceElement)
    {
        this.sourceNode = sourceElement;
    }

    @Override
    public FlowNode getTargetNode()
    {
        return targetNode;
    }

    public void setTargetNode(FlowNodeImpl targetElement)
    {
        this.targetNode = targetElement;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("SequenceFlow [id=");
        builder.append(getId());
        builder.append(", name=");
        builder.append(getName());
        builder.append(", sourceRef=");
        builder.append(getSourceRef());
        builder.append(", targetRef=");
        builder.append(getTargetRef());
        builder.append("]");
        return builder.toString();
    }

}
