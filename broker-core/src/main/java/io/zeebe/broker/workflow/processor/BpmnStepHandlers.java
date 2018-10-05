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

import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.processor.activity.InputMappingHandler;
import io.zeebe.broker.workflow.processor.activity.OutputMappingHandler;
import io.zeebe.broker.workflow.processor.activity.PropagateTerminationHandler;
import io.zeebe.broker.workflow.processor.catchevent.SubscribeMessageHandler;
import io.zeebe.broker.workflow.processor.flownode.ConsumeTokenHandler;
import io.zeebe.broker.workflow.processor.flownode.TakeSequenceFlowHandler;
import io.zeebe.broker.workflow.processor.flownode.TerminateElementHandler;
import io.zeebe.broker.workflow.processor.gateway.ExclusiveSplitHandler;
import io.zeebe.broker.workflow.processor.gateway.ParallelSplitHandler;
import io.zeebe.broker.workflow.processor.process.CompleteProcessHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ActivateGatewayHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ParallelMergeHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.StartStatefulElementHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.TriggerEndEventHandler;
import io.zeebe.broker.workflow.processor.servicetask.CreateJobHandler;
import io.zeebe.broker.workflow.processor.servicetask.TerminateServiceTaskHandler;
import io.zeebe.broker.workflow.processor.subprocess.TerminateContainedElementsHandler;
import io.zeebe.broker.workflow.processor.subprocess.TriggerStartEventHandler;
import io.zeebe.broker.workflow.processor.timer.CreateTimerHandler;
import io.zeebe.broker.workflow.processor.timer.DueDateTimerChecker;
import io.zeebe.broker.workflow.processor.timer.TerminateTimerHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumMap;
import java.util.Map;

public class BpmnStepHandlers {

  private final Map<BpmnStep, BpmnStepHandler> stepHandlers = new EnumMap<>(BpmnStep.class);

  public BpmnStepHandlers(
      SubscriptionCommandSender subscriptionCommandSender,
      WorkflowState workflowState,
      DueDateTimerChecker timerChecker) {

    // activity
    stepHandlers.put(BpmnStep.CREATE_JOB, new CreateJobHandler());
    stepHandlers.put(BpmnStep.APPLY_INPUT_MAPPING, new InputMappingHandler());
    stepHandlers.put(BpmnStep.APPLY_OUTPUT_MAPPING, new OutputMappingHandler());

    // exclusive gateway
    stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitHandler());

    // flow node
    stepHandlers.put(BpmnStep.CONSUME_TOKEN, new ConsumeTokenHandler(workflowState));
    stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowHandler());

    // sequence flow
    stepHandlers.put(BpmnStep.ACTIVATE_GATEWAY, new ActivateGatewayHandler());
    stepHandlers.put(BpmnStep.START_STATEFUL_ELEMENT, new StartStatefulElementHandler());
    stepHandlers.put(BpmnStep.TRIGGER_END_EVENT, new TriggerEndEventHandler());
    stepHandlers.put(BpmnStep.PARALLEL_MERGE, new ParallelMergeHandler(workflowState));

    // flow element container (process, sub process)
    stepHandlers.put(BpmnStep.TRIGGER_START_EVENT, new TriggerStartEventHandler());

    // parallel gateway
    stepHandlers.put(BpmnStep.PARALLEL_SPLIT, new ParallelSplitHandler());

    // termination
    stepHandlers.put(BpmnStep.TERMINATE_ELEMENT, new TerminateElementHandler());
    stepHandlers.put(BpmnStep.TERMINATE_JOB_TASK, new TerminateServiceTaskHandler());
    stepHandlers.put(BpmnStep.TERMINATE_TIMER, new TerminateTimerHandler(workflowState));
    stepHandlers.put(
        BpmnStep.TERMINATE_CONTAINED_INSTANCES,
        new TerminateContainedElementsHandler(workflowState));
    stepHandlers.put(BpmnStep.PROPAGATE_TERMINATION, new PropagateTerminationHandler());

    // intermediate catch event
    stepHandlers.put(
        BpmnStep.SUBSCRIBE_TO_INTERMEDIATE_MESSAGE,
        new SubscribeMessageHandler(subscriptionCommandSender, workflowState));
    stepHandlers.put(BpmnStep.CREATE_TIMER, new CreateTimerHandler());

    // process
    stepHandlers.put(BpmnStep.COMPLETE_PROCESS, new CompleteProcessHandler());
  }

  public void handle(BpmnStepContext context) {
    final ExecutableFlowElement flowElement = context.getElement();
    final BpmnStep step =
        flowElement.getStep((WorkflowInstanceIntent) context.getRecord().getMetadata().getIntent());

    if (step != null) {
      final BpmnStepHandler stepHandler = stepHandlers.get(step);

      if (stepHandler != null) {
        stepHandler.handle(context);
      }
    }
  }
}
