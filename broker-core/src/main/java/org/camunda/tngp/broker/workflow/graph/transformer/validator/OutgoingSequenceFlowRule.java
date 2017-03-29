/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.workflow.graph.transformer.validator;

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
        else if (outgoingSequenceFlows.isEmpty())
        {
            validationResultCollector.addError(ValidationCodes.NO_OUTGOING_SEQUENCE_FLOW, "The flow element must have one outgoing sequence flow.");
        }
        else if (outgoingSequenceFlows.size() > 1)
        {
            validationResultCollector.addError(ValidationCodes.MORE_THAN_ONE_OUTGOING_SEQUENCE_FLOW, "The flow element must not have more than one outgoing sequence flow.");
        }
    }

}
