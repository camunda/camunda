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
import io.zeebe.engine.processor.workflow.WorkflowInstanceLifecycle;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractTerminalStateHandler;
import io.zeebe.engine.processor.workflow.handlers.IncidentResolver;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Once terminated, consumes its token, and if it is the last active element in its flow scope, will
 * terminate the flow scope. If it has no flow scope (e.g. the process), does nothing.
 *
 * @param <T>
 */
public class ElementTerminatedHandler<T extends ExecutableFlowNode>
    extends AbstractTerminalStateHandler<T> {
  private final IncidentResolver incidentResolver;

  public ElementTerminatedHandler(IncidentResolver incidentResolver) {
    super();
    this.incidentResolver = incidentResolver;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isStateSameAsElementState(context);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ElementInstance flowScopeInstance = context.getFlowScopeInstance();
    final boolean isScopeTerminating =
        flowScopeInstance != null
            && WorkflowInstanceLifecycle.canTransition(
                flowScopeInstance.getState(), WorkflowInstanceIntent.ELEMENT_TERMINATED);

    incidentResolver.resolveIncidents(context);
    if (isScopeTerminating && isLastActiveExecutionPathInScope(context)) {
      context
          .getOutput()
          .appendFollowUpEvent(
              flowScopeInstance.getKey(),
              WorkflowInstanceIntent.ELEMENT_TERMINATED,
              flowScopeInstance.getValue());
    }

    return super.handleState(context);
  }
}
