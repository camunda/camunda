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
package io.zeebe.broker.workflow.processor;

import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.processor.activity.ActivateActivityHandler;
import io.zeebe.broker.workflow.processor.activity.CompleteActivityHandler;
import io.zeebe.broker.workflow.processor.activity.TerminateActivityHandler;
import io.zeebe.broker.workflow.processor.event.ActivateEventHandler;
import io.zeebe.broker.workflow.processor.event.ApplyEventHandler;
import io.zeebe.broker.workflow.processor.event.SubscribeEventHandler;
import io.zeebe.broker.workflow.processor.event.TriggerBoundaryEventHandler;
import io.zeebe.broker.workflow.processor.event.TriggerEventBasedGateway;
import io.zeebe.broker.workflow.processor.event.TriggerEventHandler;
import io.zeebe.broker.workflow.processor.event.TriggerReceiveTaskHandler;
import io.zeebe.broker.workflow.processor.flownode.ActivateFlowNodeHandler;
import io.zeebe.broker.workflow.processor.flownode.CompleteFlowNodeHandler;
import io.zeebe.broker.workflow.processor.flownode.ConsumeTokenHandler;
import io.zeebe.broker.workflow.processor.flownode.PropagateTerminationHandler;
import io.zeebe.broker.workflow.processor.gateway.ExclusiveSplitHandler;
import io.zeebe.broker.workflow.processor.gateway.ParallelSplitHandler;
import io.zeebe.broker.workflow.processor.instance.CreateWorkflowInstanceOnStartEventHandler;
import io.zeebe.broker.workflow.processor.process.CompleteProcessHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ParallelMergeHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.StartFlowNodeHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.TakeSequenceFlowHandler;
import io.zeebe.broker.workflow.processor.servicetask.ActivateReceiveTaskHandler;
import io.zeebe.broker.workflow.processor.servicetask.CreateJobHandler;
import io.zeebe.broker.workflow.processor.servicetask.TerminateServiceTaskHandler;
import io.zeebe.broker.workflow.processor.subprocess.TerminateContainedElementsHandler;
import io.zeebe.broker.workflow.processor.subprocess.TriggerStartEventHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.Map;

public class BpmnStepHandlers {
  private final Map<BpmnStep, BpmnStepHandler<?>> stepHandlers = new EnumMap<>(BpmnStep.class);

  public BpmnStepHandlers(WorkflowState workflowState, ZeebeState zeebeState) {
    final IncidentState incidentState = zeebeState.getIncidentState();

    // flow element container (process, sub process)
    stepHandlers.put(BpmnStep.TRIGGER_START_EVENT, new TriggerStartEventHandler(workflowState));

    stepHandlers.put(
        BpmnStep.CREATE_INSTANCE_ON_START_EVENT,
        new CreateWorkflowInstanceOnStartEventHandler(zeebeState));

    stepHandlers.put(BpmnStep.COMPLETE_PROCESS, new CompleteProcessHandler());
    stepHandlers.put(
        BpmnStep.TERMINATE_CONTAINED_INSTANCES, new TerminateContainedElementsHandler(zeebeState));

    // flow nodes
    stepHandlers.put(BpmnStep.ACTIVATE_FLOW_NODE, new ActivateFlowNodeHandler<>());
    stepHandlers.put(BpmnStep.COMPLETE_FLOW_NODE, new CompleteFlowNodeHandler());
    stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowHandler());
    stepHandlers.put(BpmnStep.CONSUME_TOKEN, new ConsumeTokenHandler());
    stepHandlers.put(BpmnStep.PROPAGATE_TERMINATION, new PropagateTerminationHandler());

    // activity
    stepHandlers.put(BpmnStep.ACTIVATE_ACTIVITY, new ActivateActivityHandler());
    stepHandlers.put(BpmnStep.COMPLETE_ACTIVITY, new CompleteActivityHandler());
    stepHandlers.put(BpmnStep.TERMINATE_ACTIVITY, new TerminateActivityHandler(incidentState));

    // task
    stepHandlers.put(BpmnStep.CREATE_JOB, new CreateJobHandler());
    stepHandlers.put(BpmnStep.TERMINATE_JOB_TASK, new TerminateServiceTaskHandler(zeebeState));
    stepHandlers.put(BpmnStep.ACTIVATE_RECEIVE_TASK, new ActivateReceiveTaskHandler());

    // exclusive gateway
    stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitHandler());

    // parallel gateway
    stepHandlers.put(BpmnStep.PARALLEL_SPLIT, new ParallelSplitHandler());

    // events
    stepHandlers.put(BpmnStep.ACTIVATE_EVENT, new ActivateEventHandler());
    stepHandlers.put(BpmnStep.SUBSCRIBE_TO_EVENTS, new SubscribeEventHandler());
    stepHandlers.put(BpmnStep.APPLY_EVENT, new ApplyEventHandler());
    stepHandlers.put(BpmnStep.TRIGGER_EVENT, new TriggerEventHandler());
    stepHandlers.put(BpmnStep.TRIGGER_EVENT_BASED_GATEWAY, new TriggerEventBasedGateway());
    stepHandlers.put(BpmnStep.TRIGGER_BOUNDARY_EVENT, new TriggerBoundaryEventHandler());
    stepHandlers.put(BpmnStep.TRIGGER_RECEIVE_TASK, new TriggerReceiveTaskHandler());

    // sequence flow
    stepHandlers.put(
        BpmnStep.ENTER_FLOW_NODE, new StartFlowNodeHandler(WorkflowInstanceIntent.ELEMENT_READY));
    stepHandlers.put(
        BpmnStep.ENTER_EVENT, new StartFlowNodeHandler(WorkflowInstanceIntent.EVENT_ACTIVATING));
    stepHandlers.put(
        BpmnStep.ACTIVATE_GATEWAY,
        new StartFlowNodeHandler(WorkflowInstanceIntent.GATEWAY_ACTIVATED));
    stepHandlers.put(BpmnStep.PARALLEL_MERGE, new ParallelMergeHandler(workflowState));
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
