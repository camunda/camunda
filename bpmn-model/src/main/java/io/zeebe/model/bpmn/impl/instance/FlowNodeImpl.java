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

    @Override
    public List<SequenceFlow> getIncomingSequenceFlows()
    {
        return (List) incoming;
    }

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
