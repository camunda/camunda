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
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventSupplier;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.protocol.ErrorType;

public class CatchEventSubscriber {
  private final CatchEventBehavior catchEventBehavior;

  public CatchEventSubscriber(CatchEventBehavior catchEventBehavior) {
    this.catchEventBehavior = catchEventBehavior;
  }

  public <T extends ExecutableCatchEventSupplier> boolean subscribeToEvents(
      BpmnStepContext<T> context) {
    try {
      catchEventBehavior.subscribeToEvents(context, context.getElement());
      return true;
    } catch (MessageCorrelationKeyException e) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR, e.getContext().getVariablesScopeKey(), e.getMessage());
    }

    return false;
  }

  public <T extends ExecutableCatchEventSupplier> void unsubscribeFromEvents(
      BpmnStepContext<T> context) {
    catchEventBehavior.unsubscribeFromEvents(context.getRecord().getKey(), context);
  }
}
