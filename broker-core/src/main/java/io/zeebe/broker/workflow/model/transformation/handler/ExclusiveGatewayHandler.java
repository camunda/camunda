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
import io.zeebe.broker.workflow.model.element.ExecutableExclusiveGateway;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.Collection;

public class ExclusiveGatewayHandler implements ModelElementTransformer<ExclusiveGateway> {

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

    bindLifecycle(element, gateway, context);
  }

  private void bindLifecycle(
      ExclusiveGateway element,
      final ExecutableExclusiveGateway gateway,
      TransformContext context) {
    final Collection<SequenceFlow> outgoingFlows = element.getOutgoing();

    if (outgoingFlows.size() >= 1
        && outgoingFlows.iterator().next().getConditionExpression() != null) {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.GATEWAY_ACTIVATED, BpmnStep.EXCLUSIVE_SPLIT);
    } else {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.GATEWAY_ACTIVATED, context.getCurrentFlowNodeOutgoingStep());
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
