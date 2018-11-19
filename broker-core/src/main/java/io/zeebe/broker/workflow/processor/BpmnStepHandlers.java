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
import io.zeebe.broker.workflow.processor.boundary.TriggerBoundaryEventHandler;
import io.zeebe.broker.workflow.processor.flownode.ActivateFlowNodeHandler;
import io.zeebe.broker.workflow.processor.flownode.CompleteFlowNodeHandler;
import io.zeebe.broker.workflow.processor.flownode.ConsumeTokenHandler;
import io.zeebe.broker.workflow.processor.flownode.PropagateTerminationHandler;
import io.zeebe.broker.workflow.processor.flownode.TakeSequenceFlowHandler;
import io.zeebe.broker.workflow.processor.flownode.TerminateFlowNodeHandler;
import io.zeebe.broker.workflow.processor.gateway.ExclusiveSplitHandler;
import io.zeebe.broker.workflow.processor.gateway.ParallelSplitHandler;
import io.zeebe.broker.workflow.processor.message.MessageCatchElementHandler;
import io.zeebe.broker.workflow.processor.message.TerminateIntermediateMessageHandler;
import io.zeebe.broker.workflow.processor.process.CompleteProcessHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ActivateGatewayHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ParallelMergeHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.StartFlowNodeHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.TriggerEndEventHandler;
import io.zeebe.broker.workflow.processor.servicetask.CreateJobHandler;
import io.zeebe.broker.workflow.processor.servicetask.TerminateServiceTaskHandler;
import io.zeebe.broker.workflow.processor.subprocess.TerminateContainedElementsHandler;
import io.zeebe.broker.workflow.processor.subprocess.TriggerStartEventHandler;
import io.zeebe.broker.workflow.processor.timer.CreateTimerHandler;
import io.zeebe.broker.workflow.processor.timer.TerminateTimerHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.Map;

public class BpmnStepHandlers {
  private final Map<BpmnStep, BpmnStepHandler> stepHandlers = new EnumMap<>(BpmnStep.class);

  public BpmnStepHandlers(WorkflowState workflowState, ZeebeState zeebeState) {
    final IncidentState incidentState = zeebeState.getIncidentState();

    // activity
    stepHandlers.put(BpmnStep.ACTIVATE_ACTIVITY, new ActivateActivityHandler());
    stepHandlers.put(BpmnStep.COMPLETE_ACTIVITY, new CompleteActivityHandler());
    stepHandlers.put(BpmnStep.TERMINATE_ACTIVITY, new TerminateActivityHandler(incidentState));

    // boundary events
    stepHandlers.put(BpmnStep.TRIGGER_BOUNDARY_EVENT, new TriggerBoundaryEventHandler());

    // service task
    stepHandlers.put(BpmnStep.CREATE_JOB, new CreateJobHandler());

    // exclusive gateway
    stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitHandler());

    // flow nodes
    stepHandlers.put(BpmnStep.CONSUME_TOKEN, new ConsumeTokenHandler());
    stepHandlers.put(BpmnStep.START_FLOW_NODE, new StartFlowNodeHandler());
    stepHandlers.put(BpmnStep.ACTIVATE_FLOW_NODE, new ActivateFlowNodeHandler());
    stepHandlers.put(BpmnStep.COMPLETE_FLOW_NODE, new CompleteFlowNodeHandler());
    stepHandlers.put(BpmnStep.TERMINATE_ELEMENT, new TerminateFlowNodeHandler(incidentState));

    // sequence flow
    stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowHandler());
    stepHandlers.put(BpmnStep.ACTIVATE_GATEWAY, new ActivateGatewayHandler());
    stepHandlers.put(BpmnStep.TRIGGER_END_EVENT, new TriggerEndEventHandler());
    stepHandlers.put(BpmnStep.PARALLEL_MERGE, new ParallelMergeHandler(workflowState));

    // flow element container (process, sub process)
    stepHandlers.put(BpmnStep.TRIGGER_START_EVENT, new TriggerStartEventHandler());

    // parallel gateway
    stepHandlers.put(BpmnStep.PARALLEL_SPLIT, new ParallelSplitHandler());

    // termination
    stepHandlers.put(BpmnStep.TERMINATE_JOB_TASK, new TerminateServiceTaskHandler(zeebeState));
    stepHandlers.put(BpmnStep.TERMINATE_TIMER, new TerminateTimerHandler(zeebeState));
    stepHandlers.put(
        BpmnStep.TERMINATE_INTERMEDIATE_MESSAGE,
        new TerminateIntermediateMessageHandler(zeebeState));
    stepHandlers.put(
        BpmnStep.TERMINATE_CONTAINED_INSTANCES, new TerminateContainedElementsHandler(zeebeState));
    stepHandlers.put(BpmnStep.PROPAGATE_TERMINATION, new PropagateTerminationHandler());

    // intermediate catch event
    stepHandlers.put(BpmnStep.SUBSCRIBE_TO_INTERMEDIATE_MESSAGE, new MessageCatchElementHandler());
    stepHandlers.put(BpmnStep.CREATE_TIMER, new CreateTimerHandler());

    // process
    stepHandlers.put(BpmnStep.COMPLETE_PROCESS, new CompleteProcessHandler());
  }

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
