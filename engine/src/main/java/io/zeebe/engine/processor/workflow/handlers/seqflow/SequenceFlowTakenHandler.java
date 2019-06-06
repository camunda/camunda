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
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class SequenceFlowTakenHandler<T extends ExecutableSequenceFlow> extends AbstractHandler<T> {
  public SequenceFlowTakenHandler() {
    super(null);
  }

  public SequenceFlowTakenHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isElementActive(context.getFlowScopeInstance());
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode targetNode = sequenceFlow.getTarget();

    final WorkflowInstanceRecord value = context.getValue();
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, value, targetNode);

    return true;
  }
}
