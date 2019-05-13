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
package io.zeebe.engine.processor.workflow.handlers.catchevent;

import io.zeebe.engine.Loggers;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.state.instance.EventTrigger;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class IntermediateCatchEventEventOccurredHandler<T extends ExecutableCatchEventElement>
    extends EventOccurredHandler<T> {
  public IntermediateCatchEventEventOccurredHandler() {
    this(null);
  }

  public IntermediateCatchEventEventOccurredHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      final EventTrigger event = getTriggeredEvent(context, context.getRecord().getKey());
      if (event == null) {
        Loggers.WORKFLOW_PROCESSOR_LOGGER.debug(
            "Processing EVENT_OCCURRED but no event trigger found for element {}",
            context.getElementInstance());
        return false;
      }

      processEventTrigger(
          context, context.getRecord().getKey(), context.getRecord().getKey(), event);
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_COMPLETING);
      return true;
    }

    return false;
  }
}
