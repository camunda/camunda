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
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableReceiveTask;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ReceiveTaskTransformer implements ModelElementTransformer<ReceiveTask> {

  @Override
  public Class<ReceiveTask> getType() {
    return ReceiveTask.class;
  }

  @Override
  public void transform(ReceiveTask element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableReceiveTask executableElement =
        workflow.getElementById(element.getId(), ExecutableReceiveTask.class);

    final Message message = element.getMessage();

    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);

    bindLifecycle(executableElement);
  }

  private void bindLifecycle(final ExecutableReceiveTask executableElement) {
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.RECEIVE_TASK_EVENT_OCCURRED);
  }
}
