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
package io.zeebe.engine.processor.workflow.handlers.container;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

public class ContainerElementTerminatingHandler<T extends ExecutableFlowElementContainer>
    extends ActivityElementTerminatingHandler<T> {

  public ContainerElementTerminatingHandler(CatchEventSubscriber catchEventSubscriber) {
    this(null, catchEventSubscriber);
  }

  public ContainerElementTerminatingHandler(
      WorkflowInstanceIntent nextState, CatchEventSubscriber catchEventSubscriber) {
    super(nextState, catchEventSubscriber);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    if (!super.handleState(context)) {
      return false;
    }

    final ElementInstance elementInstance = context.getElementInstance();
    final EventOutput output = context.getOutput();
    final ElementInstanceState elementInstanceState = context.getElementInstanceState();

    final List<ElementInstance> children =
        elementInstanceState.getChildren(elementInstance.getKey());

    if (children.isEmpty()) {
      transitionTo(context, WorkflowInstanceIntent.ELEMENT_TERMINATED);
    } else {
      for (final ElementInstance child : children) {
        if (child.canTerminate()) {
          output.appendFollowUpEvent(
              child.getKey(), WorkflowInstanceIntent.ELEMENT_TERMINATING, child.getValue());
        }
      }
    }

    return true;
  }
}
