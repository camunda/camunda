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
package io.zeebe.broker.workflow.graph.transformer;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import io.zeebe.broker.workflow.graph.model.BpmnAspect;
import io.zeebe.broker.workflow.graph.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;
import io.zeebe.broker.workflow.graph.model.ExecutableSequenceFlow;

public class FlowNodeTransformer implements BpmnElementTransformer<FlowNode, ExecutableFlowNode>
{
    @Override
    public Class<FlowNode> getType()
    {
        return FlowNode.class;
    }

    @Override
    public void transform(FlowNode modelElement, ExecutableFlowNode bpmnElement, ExecutableScope scope)
    {
        final List<ExecutableSequenceFlow> outgoingSequenceFlows = new ArrayList<>();
        final List<ExecutableSequenceFlow> incomingSequenceFlows = new ArrayList<>();

        for (SequenceFlow sequenceFlow : modelElement.getOutgoing())
        {
            outgoingSequenceFlows.add((ExecutableSequenceFlow) scope.getChildById(sequenceFlow.getId()));
        }

        for (SequenceFlow sequenceFlow : modelElement.getIncoming())
        {
            incomingSequenceFlows.add((ExecutableSequenceFlow) scope.getChildById(sequenceFlow.getId()));
        }

        bpmnElement.setOutgoingSequenceFlows(outgoingSequenceFlows.toArray(new ExecutableSequenceFlow[outgoingSequenceFlows.size()]));
        bpmnElement.setIncomingSequenceFlows(incomingSequenceFlows.toArray(new ExecutableSequenceFlow[incomingSequenceFlows.size()]));

        // might be extracted later
        if (outgoingSequenceFlows.isEmpty())
        {
            bpmnElement.setBpmnAspect(BpmnAspect.CONSUME_TOKEN);
        }
        else if (outgoingSequenceFlows.size() == 1)
        {
            bpmnElement.setBpmnAspect(BpmnAspect.TAKE_SEQUENCE_FLOW);
        }
    }

}
