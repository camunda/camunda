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
package io.zeebe.broker.workflow.processor.subprocess;

import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.processor.EventOutput;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

public class TerminateContainedElementsHandler
    implements BpmnStepHandler<ExecutableFlowElementContainer> {
  private final WorkflowState workflowState;

  public TerminateContainedElementsHandler(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowElementContainer> context) {
    final ElementInstance elementInstance = context.getElementInstance();
    final EventOutput output = context.getOutput();
    final List<ElementInstance> children =
        workflowState.getElementInstanceState().getChildren(elementInstance.getKey());

    context.getCatchEventOutput().unsubscribeFromCatchEvents(context);

    if (children.isEmpty()) {
      if (elementInstance.isInterrupted()) {
        context
            .getCatchEventOutput()
            .triggerBoundaryEventFromInterruptedElement(elementInstance, output.getStreamWriter());
      }

      output.appendFollowUpEvent(
          context.getRecord().getKey(),
          WorkflowInstanceIntent.ELEMENT_TERMINATED,
          context.getValue());
    } else {
      for (final ElementInstance child : children) {
        if (child.canTerminate()) {
          output.appendFollowUpEvent(
              child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());
        }
      }
    }
  }
}
