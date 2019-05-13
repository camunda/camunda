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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

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

    // in the case of events bound to a gateway, we use pass through semantics and will not actually
    // need any lifecycle
    if (!isAttachedToEventBasedGateway(executableElement)) {
      bindLifecycle(executableElement);
    }
  }

  private boolean isAttachedToEventBasedGateway(ExecutableCatchEventElement element) {
    final List<ExecutableSequenceFlow> incoming = element.getIncoming();
    if (!incoming.isEmpty()) {
      final ExecutableFlowNode source = incoming.get(0).getSource();
      return source.getElementType() == BpmnElementType.EVENT_BASED_GATEWAY;
    }

    return false;
  }

  private void bindLifecycle(ExecutableCatchEventElement executableElement) {
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING,
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATED);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.INTERMEDIATE_CATCH_EVENT_EVENT_OCCURRED);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING,
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_COMPLETING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING,
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_TERMINATING);
  }
}
