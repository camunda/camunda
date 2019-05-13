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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.Collection;

public class ExclusiveGatewayTransformer implements ModelElementTransformer<ExclusiveGateway> {

  @Override
  public Class<ExclusiveGateway> getType() {
    return ExclusiveGateway.class;
  }

  @Override
  public void transform(ExclusiveGateway element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableExclusiveGateway gateway =
        workflow.getElementById(element.getId(), ExecutableExclusiveGateway.class);

    transformDefaultFlow(element, workflow, gateway);
    bindLifecycle(gateway);
  }

  private void bindLifecycle(final ExecutableExclusiveGateway gateway) {
    final Collection<ExecutableSequenceFlow> outgoingFlows = gateway.getOutgoing();
    final boolean hasNoOutgoingFlows = outgoingFlows.size() == 0;
    final boolean hasSingleNonConditionalOutgoingFlow =
        outgoingFlows.size() == 1 && outgoingFlows.iterator().next().getCondition() == null;

    if (hasNoOutgoingFlows || hasSingleNonConditionalOutgoingFlow) {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    } else {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_ACTIVATING);
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_COMPLETED);
    }
  }

  private void transformDefaultFlow(
      ExclusiveGateway element,
      final ExecutableWorkflow workflow,
      final ExecutableExclusiveGateway gateway) {
    final SequenceFlow defaultFlowElement = element.getDefault();

    if (defaultFlowElement != null) {
      final String defaultFlowId = defaultFlowElement.getId();
      final ExecutableSequenceFlow defaultFlow =
          workflow.getElementById(defaultFlowId, ExecutableSequenceFlow.class);

      gateway.setDefaultFlow(defaultFlow);
    }
  }
}
