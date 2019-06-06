/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.handlers.seqflow;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

/**
 * ELEMENT_COMPLETED handler for elements which should take outgoing sequence flows without
 * prejudice.
 *
 * @param <T>
 */
public class FlowOutElementCompletedHandler<T extends ExecutableFlowNode>
    extends ElementCompletedHandler<T> {
  public FlowOutElementCompletedHandler() {
    super();
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final List<ExecutableSequenceFlow> outgoing = context.getElement().getOutgoing();

    for (final ExecutableSequenceFlow flow : outgoing) {
      takeSequenceFlow(context, flow);
    }

    return super.handleState(context);
  }

  private void takeSequenceFlow(BpmnStepContext<T> context, ExecutableSequenceFlow flow) {
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, context.getValue(), flow);
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    flowScopeInstance.spawnToken();
    context.getStateDb().getElementInstanceState().updateInstance(flowScopeInstance);
  }
}
