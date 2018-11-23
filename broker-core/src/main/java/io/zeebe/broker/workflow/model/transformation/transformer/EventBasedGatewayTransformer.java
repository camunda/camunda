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
import io.zeebe.broker.workflow.model.element.ExecutableEventBasedGateway;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
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

    final List<ExecutableCatchEventElement> connectedEvents = getConnectedCatchEvents(gateway);
    gateway.setEvents(connectedEvents);

    bindLifecycle(element, gateway, context);

    // configure the lifecycle of the connected events
    connectedEvents.forEach(event -> bindLifecycle(event, context));
  }

  private List<ExecutableCatchEventElement> getConnectedCatchEvents(
      final ExecutableEventBasedGateway gateway) {
    return gateway
        .getOutgoing()
        .stream()
        .map(e -> (ExecutableCatchEventElement) e.getTarget())
        .collect(Collectors.toList());
  }

  private void bindLifecycle(
      EventBasedGateway element,
      final ExecutableEventBasedGateway gateway,
      TransformContext context) {

    gateway.bindLifecycleState(
        WorkflowInstanceIntent.GATEWAY_ACTIVATED, BpmnStep.SUBSCRIBE_TO_EVENTS);
  }

  private void bindLifecycle(ExecutableCatchEventElement event, TransformContext context) {
    event.bindLifecycleState(
        WorkflowInstanceIntent.CATCH_EVENT_TRIGGERING, BpmnStep.TRIGGER_EVENT_BASED_GATEWAY);
    event.bindLifecycleState(
        WorkflowInstanceIntent.CATCH_EVENT_TRIGGERED, context.getCurrentFlowNodeOutgoingStep());
  }
}
