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

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.model.element.ExecutableReceiveTask;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ReceiveTaskHandler implements ModelElementTransformer<ReceiveTask> {

  @Override
  public Class<ReceiveTask> getType() {
    return ReceiveTask.class;
  }

  @Override
  public void transform(ReceiveTask element, TransformContext context) {

    // only message supported at this point

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableReceiveTask executableElement =
        workflow.getElementById(element.getId(), ExecutableReceiveTask.class);

    final Message message = element.getMessage();

    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);

    bindLifecycle(context, executableElement);
  }

  private void bindLifecycle(
      TransformContext context, final ExecutableReceiveTask executableElement) {

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_READY, BpmnStep.APPLY_INPUT_MAPPING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.SUBSCRIBE_TO_INTERMEDIATE_MESSAGE);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.APPLY_OUTPUT_MAPPING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, context.getCurrentFlowNodeOutgoingStep());
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_ELEMENT);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.PROPAGATE_TERMINATION);
  }
}
