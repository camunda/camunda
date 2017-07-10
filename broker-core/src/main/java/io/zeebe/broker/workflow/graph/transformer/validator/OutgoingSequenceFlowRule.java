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
package io.zeebe.broker.workflow.graph.transformer.validator;

import java.util.Collection;

import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class OutgoingSequenceFlowRule implements ModelElementValidator<FlowNode>
{

    @Override
    public Class<FlowNode> getElementType()
    {
        return FlowNode.class;
    }

    @Override
    public void validate(FlowNode flowNode, ValidationResultCollector validationResultCollector)
    {
        final Collection<SequenceFlow> outgoingSequenceFlows = flowNode.getOutgoing();

        if (flowNode instanceof EndEvent)
        {
            if (!outgoingSequenceFlows.isEmpty())
            {
                validationResultCollector.addError(ValidationCodes.OUTGOING_SEQUENCE_FLOW_AT_END_EVENT, "An end event must not have an outgoing sequence flow.");
            }
        }
        else if (outgoingSequenceFlows.size() > 1)
        {
            validationResultCollector.addError(ValidationCodes.MORE_THAN_ONE_OUTGOING_SEQUENCE_FLOW, "The flow element must not have more than one outgoing sequence flow.");
        }
    }

}
