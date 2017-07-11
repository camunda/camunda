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

import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;

import io.zeebe.broker.workflow.graph.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;
import io.zeebe.broker.workflow.graph.model.ExecutableSequenceFlow;

public class SequenceFlowTransformer implements BpmnElementTransformer<SequenceFlow, ExecutableSequenceFlow>
{
    @Override
    public Class<SequenceFlow> getType()
    {
        return SequenceFlow.class;
    }

    @Override
    public void transform(SequenceFlow modelElement, ExecutableSequenceFlow bpmnElement, ExecutableScope scope)
    {
        final FlowNode source = modelElement.getSource();
        final FlowNode target = modelElement.getTarget();

        bpmnElement.setSourceNode((ExecutableFlowNode) scope.getChildById(source.getId()));
        bpmnElement.setTargetNode((ExecutableFlowNode) scope.getChildById(target.getId()));
    }

}
