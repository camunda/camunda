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

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.workflow.model.element.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionException;
import io.zeebe.msgpack.el.JsonConditionInterpreter;
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
        value.setActivityId(sequenceFlow.getId());
        context.getOutput().writeNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, value);
      } else {
        final String errorMessage = "All conditions evaluated to false and no default flow is set.";
        raiseIncident(context, errorMessage);
      }
    } catch (JsonConditionException e) {
      raiseIncident(context, e.getMessage());
    }
  }

  private void raiseIncident(
      BpmnStepContext<ExecutableExclusiveGateway> context, final String errorMessage) {
    context.raiseIncident(ErrorType.CONDITION_ERROR, errorMessage);

    // TODO: this is a hack to avoid that we believe this token is consumed when the incident is
    // raised (because no follow-up token event is published), which could wrongfully lead to
    // scope completion on a parallel branch.
    // Ideas to resolve this:
    //     - explicitly represent incidents in the index, so we can consider them when checking if a
    // scope can complete etc.
    //     - rework incident concept via https://github.com/zeebe-io/zeebe/issues/1033; maybe this
    // will also then go away
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    flowScopeInstance.spawnToken();
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
