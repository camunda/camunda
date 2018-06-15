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

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SequenceFlow;

public class FlowNodeImpl extends FlowElementImpl implements FlowNode
{
    private List<SequenceFlowImpl> incoming = new ArrayList<>();
    private List<SequenceFlowImpl> outgoing = new ArrayList<>();

    @XmlIDREF
    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_INCOMING, namespace = BpmnConstants.BPMN20_NS)
    public void setIncoming(List<SequenceFlowImpl> incoming)
    {
        this.incoming.addAll(incoming);
    }

    public List<SequenceFlowImpl> getIncoming()
    {
        return incoming;
    }

    @XmlIDREF
    @XmlElement(name = BpmnConstants.BPMN_ELEMENT_OUTGOING, namespace = BpmnConstants.BPMN20_NS)
    public void setOutgoing(List<SequenceFlowImpl> outgoing)
    {
        this.outgoing.addAll(outgoing);
    }

    public List<SequenceFlowImpl> getOutgoing()
    {
        return outgoing;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public List<SequenceFlow> getIncomingSequenceFlows()
    {
        return (List) incoming;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public List<SequenceFlow> getOutgoingSequenceFlows()
    {
        return (List) outgoing;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("FlowNode [id=");
        builder.append(getId());
        builder.append(", name=");
        builder.append(getName());
        builder.append(", incoming=");
        builder.append(incoming);
        builder.append(", outgoing=");
        builder.append(outgoing);
        builder.append("]");
        return builder.toString();
    }

}
