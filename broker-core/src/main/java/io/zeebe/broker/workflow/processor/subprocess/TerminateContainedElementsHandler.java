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

  public TerminateContainedElementsHandler(WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowElementContainer> context) {
    final ElementInstance subProcessInstance = context.getElementInstance();

    final List<ElementInstance> children =
        workflowState.getElementInstanceState().getChildren(subProcessInstance.getKey());
    final EventOutput streamWriter = context.getOutput();

    if (children.isEmpty()) {
      streamWriter.writeFollowUpEvent(
          context.getRecord().getKey(),
          WorkflowInstanceIntent.ELEMENT_TERMINATED,
          context.getValue());
    } else {

      streamWriter.newBatch();

      for (int i = 0; i < children.size(); i++) {
        final ElementInstance child = children.get(i);

        if (child.canTerminate()) {
          context
              .getOutput()
              .writeFollowUpEvent(
                  child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());
        }
      }
    }
  }
}
