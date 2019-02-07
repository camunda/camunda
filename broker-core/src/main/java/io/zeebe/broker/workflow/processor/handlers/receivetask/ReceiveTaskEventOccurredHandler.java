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
package io.zeebe.broker.workflow.processor.handlers.receivetask;

import io.zeebe.broker.workflow.model.element.ExecutableReceiveTask;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.activity.ActivityEventOccurredHandler;
import io.zeebe.broker.workflow.state.EventTrigger;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

/**
 * Differs from {@link ActivityEventOccurredHandler} in the case where the triggered event is the
 * receive task's message trigger, at which point the task will complete.
 *
 * @param <T>
 */
public class ReceiveTaskEventOccurredHandler<T extends ExecutableReceiveTask>
    extends ActivityEventOccurredHandler<T> {
  public ReceiveTaskEventOccurredHandler() {
    this(null);
  }

  public ReceiveTaskEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final EventTrigger event = getTriggeredEvent(context);
    if (event.getElementId().equals(context.getElement().getId())) {
      context.getValue().setPayload(event.getPayload());
      handleEvent(context, context.getRecord().getKey(), event);
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);

      return true;
    }

    return super.handleState(context);
  }
}
