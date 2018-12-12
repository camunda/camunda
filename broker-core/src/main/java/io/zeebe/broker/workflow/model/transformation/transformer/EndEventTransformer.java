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
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class EndEventTransformer implements ModelElementTransformer<EndEvent> {

  @Override
  public Class<EndEvent> getType() {
    return EndEvent.class;
  }

  @Override
  public void transform(EndEvent element, TransformContext context) {
    final ExecutableWorkflow currentWorkflow = context.getCurrentWorkflow();
    final ExecutableFlowNode endEvent =
        currentWorkflow.getElementById(element.getId(), ExecutableFlowNode.class);

    bindLifecycle(context, endEvent);
  }

  private void bindLifecycle(TransformContext context, ExecutableFlowNode endEvent) {
    endEvent.bindLifecycleState(WorkflowInstanceIntent.EVENT_ACTIVATING, BpmnStep.ACTIVATE_EVENT);
    endEvent.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_ACTIVATED, context.getCurrentFlowNodeOutgoingStep());
  }
}
