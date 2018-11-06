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
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ProcessHandler implements ModelElementTransformer<Process> {

  @Override
  public Class<Process> getType() {
    return Process.class;
  }

  @Override
  public void transform(Process element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getWorkflow(element.getId());
    context.setCurrentWorkflow(workflow);

    workflow.bindLifecycleState(WorkflowInstanceIntent.ELEMENT_READY, BpmnStep.APPLY_INPUT_MAPPING);
    workflow.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.TRIGGER_START_EVENT);
    workflow.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.COMPLETE_PROCESS);
    workflow.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_CONTAINED_INSTANCES);
  }
}
