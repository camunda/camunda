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
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.engine.processor.workflow.handlers.IncidentResolver;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatedHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.activity.ActivityEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.IntermediateCatchEventElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.IntermediateCatchEventElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.IntermediateCatchEventElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.IntermediateCatchEventElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.IntermediateCatchEventEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.catchevent.StartEventEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ContainerElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.container.ContainerElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatedHandler;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.element.EventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementCompletingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayElementTerminatingHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.EventBasedGatewayEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.gateway.ExclusiveGatewayElementActivatingHandler;
import io.zeebe.engine.processor.workflow.handlers.receivetask.ReceiveTaskEventOccurredHandler;
import io.zeebe.engine.processor.workflow.handlers.seqflow.FlowOutElementCompletedHandler;
import io.zeebe.engine.processor.workflow.handlers.seqflow.ParallelMergeSequenceFlowTaken;
import io.zeebe.engine.processor.workflow.handlers.seqflow.SequenceFlowTakenHandler;
import io.zeebe.engine.processor.workflow.handlers.servicetask.ServiceTaskElementActivatedHandler;
import io.zeebe.engine.processor.workflow.handlers.servicetask.ServiceTaskElementTerminatingHandler;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.Map;

public class BpmnStepHandlers {
  private final Map<BpmnStep, BpmnStepHandler<?>> stepHandlers = new EnumMap<>(BpmnStep.class);

  public BpmnStepHandlers(ZeebeState state, CatchEventBehavior catchEventBehavior) {
    final IncidentResolver incidentResolver = new IncidentResolver(state.getIncidentState());
    final CatchEventSubscriber catchEventSubscriber = new CatchEventSubscriber(catchEventBehavior);

    stepHandlers.put(BpmnStep.ELEMENT_ACTIVATING, new ElementActivatingHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_ACTIVATED, new ElementActivatedHandler<>());
    stepHandlers.put(BpmnStep.EVENT_OCCURRED, new EventOccurredHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_COMPLETING, new ElementCompletingHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_COMPLETED, new ElementCompletedHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_TERMINATING, new ElementTerminatingHandler<>());
    stepHandlers.put(BpmnStep.ELEMENT_TERMINATED, new ElementTerminatedHandler<>(incidentResolver));
    stepHandlers.put(BpmnStep.FLOWOUT_ELEMENT_COMPLETED, new FlowOutElementCompletedHandler<>());

    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_ACTIVATING,
        new ActivityElementActivatingHandler<>(catchEventSubscriber));
    stepHandlers.put(BpmnStep.ACTIVITY_ELEMENT_ACTIVATED, new ElementActivatedHandler<>(null));
    stepHandlers.put(BpmnStep.ACTIVITY_EVENT_OCCURRED, new ActivityEventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_COMPLETING,
        new ActivityElementCompletingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_TERMINATING,
        new ActivityElementTerminatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.ACTIVITY_ELEMENT_TERMINATED,
        new ActivityElementTerminatedHandler<>(incidentResolver));

    stepHandlers.put(
        BpmnStep.CONTAINER_ELEMENT_ACTIVATED,
        new ContainerElementActivatedHandler<>(state.getWorkflowState()));
    stepHandlers.put(
        BpmnStep.CONTAINER_ELEMENT_TERMINATING,
        new ContainerElementTerminatingHandler<>(catchEventSubscriber));

    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATING,
        new EventBasedGatewayElementActivatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_ACTIVATED, new ElementActivatedHandler<>(null));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_EVENT_OCCURRED, new EventBasedGatewayEventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETING,
        new EventBasedGatewayElementCompletingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_TERMINATING,
        new EventBasedGatewayElementTerminatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.EVENT_BASED_GATEWAY_ELEMENT_COMPLETED,
        new EventBasedGatewayElementCompletedHandler<>());

    stepHandlers.put(
        BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_ACTIVATING,
        new ExclusiveGatewayElementActivatingHandler<>());
    stepHandlers.put(
        BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_COMPLETED,
        new EventBasedGatewayElementCompletedHandler<>());

    stepHandlers.put(
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATING,
        new IntermediateCatchEventElementActivatingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_ACTIVATED,
        new IntermediateCatchEventElementActivatedHandler<>());
    stepHandlers.put(
        BpmnStep.INTERMEDIATE_CATCH_EVENT_EVENT_OCCURRED,
        new IntermediateCatchEventEventOccurredHandler<>());
    stepHandlers.put(
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_COMPLETING,
        new IntermediateCatchEventElementCompletingHandler<>(catchEventSubscriber));
    stepHandlers.put(
        BpmnStep.INTERMEDIATE_CATCH_EVENT_ELEMENT_TERMINATING,
        new IntermediateCatchEventElementTerminatingHandler<>(catchEventSubscriber));

    stepHandlers.put(BpmnStep.RECEIVE_TASK_EVENT_OCCURRED, new ReceiveTaskEventOccurredHandler<>());

    stepHandlers.put(
        BpmnStep.SERVICE_TASK_ELEMENT_ACTIVATED, new ServiceTaskElementActivatedHandler<>());
    stepHandlers.put(
        BpmnStep.SERVICE_TASK_ELEMENT_TERMINATING,
        new ServiceTaskElementTerminatingHandler<>(
            state.getIncidentState(), catchEventSubscriber, state.getJobState()));

    stepHandlers.put(
        BpmnStep.START_EVENT_EVENT_OCCURRED, new StartEventEventOccurredHandler<>(state));

    stepHandlers.put(
        BpmnStep.PARALLEL_MERGE_SEQUENCE_FLOW_TAKEN, new ParallelMergeSequenceFlowTaken<>());
    stepHandlers.put(BpmnStep.SEQUENCE_FLOW_TAKEN, new SequenceFlowTakenHandler<>());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void handle(BpmnStepContext context) {
    final ExecutableFlowElement flowElement = context.getElement();
    final WorkflowInstanceIntent state =
        (WorkflowInstanceIntent) context.getRecord().getMetadata().getIntent();
    final BpmnStep step = flowElement.getStep(state);

    if (step != null) {
      final BpmnStepHandler stepHandler = stepHandlers.get(step);
      assert stepHandler != null : "no step handler configured for step " + step.toString();
      stepHandler.handle(context);
    }
  }
}
