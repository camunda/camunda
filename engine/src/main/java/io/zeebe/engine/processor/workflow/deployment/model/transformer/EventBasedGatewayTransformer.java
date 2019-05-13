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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.stream.Collectors;

public class EventBasedGatewayTransformer implements ModelElementTransformer<EventBasedGateway> {

  @Override
  public Class<EventBasedGateway> getType() {
    return EventBasedGateway.class;
  }

  @Override
  public void transform(EventBasedGateway element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableEventBasedGateway gateway =
        workflow.getElementById(element.getId(), ExecutableEventBasedGateway.class);

    final List<ExecutableCatchEvent> connectedEvents = getConnectedCatchEvents(gateway);
    gateway.setEvents(connectedEvents);

    bindLifecycle(gateway);
  }

  private List<ExecutableCatchEvent> getConnectedCatchEvents(
      final ExecutableEventBasedGateway gateway) {
    return gateway.getOutgoing().stream()
        .map(e -> (ExecutableCatchEvent) e.getTarget())
        .collect(Collectors.toList());
  }

  private void bindLifecycle(final ExecutableEventBasedGateway gateway) {
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATING);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATED);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.EVENT_BASED_GATEWAY_EVENT_OCCURRED);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETING);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETED);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING,
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_TERMINATING);
  }
}
