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
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class IntermediateCatchEventTransformer
    implements ModelElementTransformer<IntermediateCatchEvent> {

  @Override
  public Class<IntermediateCatchEvent> getType() {
    return IntermediateCatchEvent.class;
  }

  @Override
  public void transform(IntermediateCatchEvent element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableCatchEventElement executableElement =
        workflow.getElementById(element.getId(), ExecutableCatchEventElement.class);

    bindLifecycle(context, executableElement);
  }

  private void bindLifecycle(
      TransformContext context, ExecutableCatchEventElement executableElement) {
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_READY, BpmnStep.ACTIVATE_FLOW_NODE);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.SUBSCRIBE_TO_EVENTS);

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.COMPLETE_FLOW_NODE);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, context.getCurrentFlowNodeOutgoingStep());

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_ACTIVITY);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.PROPAGATE_TERMINATION);
  }
}
