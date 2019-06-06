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
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractTerminalStateHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Delegates completion logic to sub class, and if successful and it is the last active element in
 * its flow scope, will terminate its flow scope.
 *
 * @param <T>
 */
public class ElementCompletedHandler<T extends ExecutableFlowNode>
    extends AbstractTerminalStateHandler<T> {
  public ElementCompletedHandler() {
    super();
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()));
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (isLastActiveExecutionPathInScope(context)) {
      completeFlowScope(context);
    }

    return super.handleState(context);
  }

  protected void completeFlowScope(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    final WorkflowInstanceRecord flowScopeInstanceValue = flowScopeInstance.getValue();

    context
        .getOutput()
        .appendFollowUpEvent(
            flowScopeInstance.getKey(),
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            flowScopeInstanceValue);
  }
}
