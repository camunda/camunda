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
package io.zeebe.broker.workflow.model.transformation.handler;

import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class StartEventHandler implements ModelElementTransformer<StartEvent> {

  @Override
  public Class<StartEvent> getType() {
    return StartEvent.class;
  }

  @Override
  public void transform(StartEvent element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableFlowNode startEvent =
        workflow.getElementById(element.getId(), ExecutableFlowNode.class);

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();

      final ExecutableFlowElementContainer subprocess =
          workflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);
      subprocess.setStartEvent(startEvent);
    } else {
      // top-level start event
      workflow.setStartEvent(startEvent);
    }

    bindLifecycle(context, startEvent);
  }

  private void bindLifecycle(TransformContext context, final ExecutableFlowNode startEvent) {
    startEvent.bindLifecycleState(
        WorkflowInstanceIntent.START_EVENT_OCCURRED, context.getCurrentFlowNodeOutgoingStep());
  }
}
