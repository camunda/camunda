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
package io.zeebe.broker.workflow.model.transformation.transformer;

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableCatchEventElement;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElementContainer;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class StartEventTransformer implements ModelElementTransformer<StartEvent> {

  @Override
  public Class<StartEvent> getType() {
    return StartEvent.class;
  }

  @Override
  public void transform(StartEvent element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableCatchEventElement startEvent =
        workflow.getElementById(element.getId(), ExecutableCatchEventElement.class);

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();

      final ExecutableFlowElementContainer subprocess =
          workflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);
      subprocess.addStartEvent(startEvent);
    } else {
      // top-level start event
      workflow.addStartEvent(startEvent);
    }

    bindLifecycle(context, startEvent);
  }

  private void bindLifecycle(
      TransformContext context, final ExecutableCatchEventElement startEvent) {
    startEvent.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.CREATE_INSTANCE_ON_START_EVENT);
    startEvent.bindLifecycleState(WorkflowInstanceIntent.EVENT_TRIGGERING, BpmnStep.APPLY_EVENT);
    startEvent.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_TRIGGERED, context.getCurrentFlowNodeOutgoingStep());
  }
}
