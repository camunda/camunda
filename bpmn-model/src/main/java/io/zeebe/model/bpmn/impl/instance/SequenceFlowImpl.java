/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import org.agrona.DirectBuffer;

public class SequenceFlowImpl extends FlowElementImpl implements SequenceFlow
{
    private DirectBuffer sourceRef;
    private DirectBuffer targetRef;

    private FlowNodeImpl sourceNode;
    private FlowNodeImpl targetNode;

    private ConditionExpressionImpl conditionExpression;

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

    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_CONDITION_EXPRESSION, namespace = BpmnConstants.BPMN20_NS)
    public void setConditionExpression(ConditionExpressionImpl conditionExpression)
    {
        this.conditionExpression = conditionExpression;
    }

    public ConditionExpressionImpl getConditionExpression()
    {
        return conditionExpression;
    }

    @Override
    public boolean hasCondition()
    {
        return conditionExpression != null;
    }

    @Override
    public CompiledJsonCondition getCondition()
    {
        return hasCondition() ? conditionExpression.getCondition() : null;
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
        builder.append(", condition=");
        builder.append(conditionExpression);
        builder.append("]");
        return builder.toString();
    }

}
