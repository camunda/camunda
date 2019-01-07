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
package io.zeebe.broker.workflow.processor.event;

import io.zeebe.broker.workflow.model.element.ExecutableBoundaryEvent;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class TriggerBoundaryEventHandler implements BpmnStepHandler<ExecutableBoundaryEvent> {

  @Override
  public void handle(BpmnStepContext<ExecutableBoundaryEvent> context) {
    final ExecutableBoundaryEvent element = context.getElement();

    if (element.cancelActivity()) {
      triggerInterruptingBoundaryEvent(context);

    } else {
      triggerNonInterruptingBoundaryEvent(context);
    }
  }

  private void triggerInterruptingBoundaryEvent(BpmnStepContext<ExecutableBoundaryEvent> context) {
    final ElementInstance elementInstance = context.getElementInstance();

    if (elementInstance != null
        && elementInstance.getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {

      context.getCatchEventBehavior().deferEvent(context);

      context
          .getOutput()
          .appendFollowUpEvent(
              context.getRecord().getKey(),
              WorkflowInstanceIntent.ELEMENT_TERMINATING,
              context.getElementInstance().getValue());
    }
  }

  private void triggerNonInterruptingBoundaryEvent(
      BpmnStepContext<ExecutableBoundaryEvent> context) {
    context
        .getOutput()
        .appendNewEvent(WorkflowInstanceIntent.EVENT_TRIGGERING, context.getRecord().getValue());

    // spawn a new token to continue at the event
    context.getFlowScopeInstance().spawnToken();
  }
}
