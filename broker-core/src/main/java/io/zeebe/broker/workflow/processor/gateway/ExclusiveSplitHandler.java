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
package io.zeebe.broker.workflow.processor.gateway;

import io.zeebe.broker.workflow.model.element.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionException;
import io.zeebe.msgpack.el.JsonConditionInterpreter;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;
import org.agrona.DirectBuffer;

public class ExclusiveSplitHandler implements BpmnStepHandler<ExecutableExclusiveGateway> {

  private final JsonConditionInterpreter conditionInterpreter = new JsonConditionInterpreter();

  @Override
  public void handle(BpmnStepContext<ExecutableExclusiveGateway> context) {
    try {
      final WorkflowInstanceRecord value = context.getValue();
      final ExecutableSequenceFlow sequenceFlow =
          getSequenceFlowWithFulfilledCondition(context.getElement(), value.getPayload());

      if (sequenceFlow != null) {
        value.setElementId(sequenceFlow.getId());
        context.getOutput().appendNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, value);
      } else {
        final String errorMessage = "All conditions evaluated to false and no default flow is set.";
        context.raiseIncident(ErrorType.CONDITION_ERROR, errorMessage);
      }
    } catch (JsonConditionException e) {
      context.raiseIncident(ErrorType.CONDITION_ERROR, e.getMessage());
    }
  }

  private ExecutableSequenceFlow getSequenceFlowWithFulfilledCondition(
      ExecutableExclusiveGateway exclusiveGateway, DirectBuffer payload) {
    final List<ExecutableSequenceFlow> sequenceFlows = exclusiveGateway.getOutgoingWithCondition();
    for (int s = 0; s < sequenceFlows.size(); s++) {
      final ExecutableSequenceFlow sequenceFlow = sequenceFlows.get(s);

      final CompiledJsonCondition compiledCondition = sequenceFlow.getCondition();
      final boolean isFulFilled =
          conditionInterpreter.eval(compiledCondition.getCondition(), payload);

      if (isFulFilled) {
        return sequenceFlow;
      }
    }
    return exclusiveGateway.getDefaultFlow();
  }
}
