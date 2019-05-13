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
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletingHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class EventBasedGatewayElementCompletingHandler<T extends ExecutableEventBasedGateway>
    extends ElementCompletingHandler<T> {
  private final CatchEventSubscriber catchEventSubscriber;

  public EventBasedGatewayElementCompletingHandler(CatchEventSubscriber catchEventSubscriber) {
    super();
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public EventBasedGatewayElementCompletingHandler(
      IOMappingHelper ioMappingHelper, CatchEventSubscriber catchEventSubscriber) {
    super(ioMappingHelper);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  public EventBasedGatewayElementCompletingHandler(
      WorkflowInstanceIntent nextState,
      IOMappingHelper ioMappingHelper,
      CatchEventSubscriber catchEventSubscriber) {
    super(nextState, ioMappingHelper);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (super.handleState(context)) {
      catchEventSubscriber.unsubscribeFromEvents(context);
      return true;
    }

    return false;
  }
}
