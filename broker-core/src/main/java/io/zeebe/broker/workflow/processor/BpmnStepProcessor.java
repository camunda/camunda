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

import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
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
import io.zeebe.broker.workflow.processor.sequenceflow.StartStatefulElementHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.TriggerEndEventHandler;
import io.zeebe.broker.workflow.processor.servicetask.CreateJobHandler;
import io.zeebe.broker.workflow.processor.servicetask.TerminateServiceTaskHandler;
import io.zeebe.broker.workflow.processor.subprocess.TerminateContainedElementsHandler;
import io.zeebe.broker.workflow.processor.subprocess.TriggerStartEventHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.metrics.MetricsManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BpmnStepProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final ElementInstanceIndex elementInstances;

  private final WorkflowCache workflowCache;
  private final Map<BpmnStep, BpmnStepHandler> stepHandlers = new EnumMap<>(BpmnStep.class);
  private final Map<WorkflowInstanceIntent, Predicate<BpmnStepContext>> stepGuards =
      new EnumMap<>(WorkflowInstanceIntent.class);

  private BpmnStepContext context;
  private WorkflowInstanceMetrics metrics;

  public BpmnStepProcessor(
      ElementInstanceIndex scopeInstances,
      WorkflowCache workflowCache,
      SubscriptionCommandSender subscriptionCommandSender,
      WorkflowInstanceSubscriptionDataStore subscriptionStore) {

    this.elementInstances = scopeInstances;
    this.workflowCache = workflowCache;

    // activity
    stepHandlers.put(BpmnStep.CREATE_JOB, new CreateJobHandler());
    stepHandlers.put(BpmnStep.APPLY_INPUT_MAPPING, new InputMappingHandler());
    stepHandlers.put(BpmnStep.APPLY_OUTPUT_MAPPING, new OutputMappingHandler());

    // exclusive gateway
    stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitHandler());

    // flow node
    stepHandlers.put(BpmnStep.CONSUME_TOKEN, new ConsumeTokenHandler());
    stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowHandler());

    // sequence flow
    stepHandlers.put(BpmnStep.ACTIVATE_GATEWAY, new ActivateGatewayHandler());
    stepHandlers.put(BpmnStep.START_STATEFUL_ELEMENT, new StartStatefulElementHandler());
    stepHandlers.put(BpmnStep.TRIGGER_END_EVENT, new TriggerEndEventHandler());

    // flow element container (process, sub process)
    stepHandlers.put(BpmnStep.TRIGGER_START_EVENT, new TriggerStartEventHandler());

    // parallel gateway
    stepHandlers.put(BpmnStep.PARALLEL_SPLIT, new ParallelSplitHandler());

    // termination
    stepHandlers.put(BpmnStep.TERMINATE_ELEMENT, new TerminateElementHandler());
    stepHandlers.put(BpmnStep.TERMINATE_JOB_TASK, new TerminateServiceTaskHandler());
    stepHandlers.put(
        BpmnStep.TERMINATE_CONTAINED_INSTANCES, new TerminateContainedElementsHandler());
    stepHandlers.put(BpmnStep.PROPAGATE_TERMINATION, new PropagateTerminationHandler());

    // intermediate catch event
    stepHandlers.put(
        BpmnStep.SUBSCRIBE_TO_INTERMEDIATE_MESSAGE,
        new SubscribeMessageHandler(subscriptionCommandSender, subscriptionStore));

    // process
    stepHandlers.put(BpmnStep.COMPLETE_PROCESS, new CompleteProcessHandler());

    // step guards: should a record in a certain state be handled?
    final Predicate<BpmnStepContext> noConcurrentTransitionGuard =
        c -> c.getState() == c.getElementInstance().getState();
    final Predicate<BpmnStepContext> scopeActiveGuard =
        c ->
            c.getFlowScopeInstance() != null
                && c.getFlowScopeInstance().getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED;
    final Predicate<BpmnStepContext> scopeTerminatingGuard =
        c ->
            c.getFlowScopeInstance() != null
                && c.getFlowScopeInstance().getState()
                    == WorkflowInstanceIntent.ELEMENT_TERMINATING;

    stepGuards.put(WorkflowInstanceIntent.ELEMENT_READY, noConcurrentTransitionGuard);
    stepGuards.put(WorkflowInstanceIntent.ELEMENT_ACTIVATED, noConcurrentTransitionGuard);
    stepGuards.put(WorkflowInstanceIntent.ELEMENT_COMPLETING, noConcurrentTransitionGuard);
    stepGuards.put(WorkflowInstanceIntent.ELEMENT_COMPLETED, scopeActiveGuard);
    stepGuards.put(WorkflowInstanceIntent.ELEMENT_TERMINATING, c -> true);
    stepGuards.put(WorkflowInstanceIntent.ELEMENT_TERMINATED, scopeTerminatingGuard);

    stepGuards.put(WorkflowInstanceIntent.END_EVENT_OCCURRED, scopeActiveGuard);
    stepGuards.put(WorkflowInstanceIntent.GATEWAY_ACTIVATED, scopeActiveGuard);
    stepGuards.put(WorkflowInstanceIntent.START_EVENT_OCCURRED, scopeActiveGuard);
    stepGuards.put(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, scopeActiveGuard);
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    final StreamProcessorContext streamProcessorContext =
        streamProcessor.getStreamProcessorContext();
    final MetricsManager metricsManager =
        streamProcessorContext.getActorScheduler().getMetricsManager();
    final LogStream logStream = streamProcessorContext.getLogStream();

    this.metrics =
        new WorkflowInstanceMetrics(
            metricsManager, logStream.getTopicName(), logStream.getPartitionId());
    this.context = new BpmnStepContext<>(elementInstances, metrics);
  }

  @Override
  public void onClose() {
    this.metrics.close();
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {

    context.setRecord(record);
    context.setStreamWriter(streamWriter);
    context.setSideEffect(sideEffect);

    final long workflowKey = record.getValue().getWorkflowKey();
    final DeployedWorkflow deployedWorkflow = workflowCache.getWorkflowByKey(workflowKey);

    if (deployedWorkflow == null) {
      throw new IllegalStateException(
          "Error while processing workflow. Workflow with " + workflowKey + " is not deployed");
    } else {
      callStepHandler(deployedWorkflow);
    }
  }

  private void callStepHandler(DeployedWorkflow deployedWorkflow) {

    populateElementInContext(deployedWorkflow);
    populateElementInstancesInContext();

    if (shallProcessRecord()) {
      final ExecutableFlowElement flowElement = context.getElement();
      final BpmnStep step =
          flowElement.getStep(
              (WorkflowInstanceIntent) context.getRecord().getMetadata().getIntent());

      if (step != null) {
        final BpmnStepHandler stepHandler = stepHandlers.get(step);

        if (stepHandler != null) {
          stepHandler.handle(context);
        }
      }
    }
  }

  private void populateElementInContext(DeployedWorkflow deployedWorkflow) {
    final WorkflowInstanceRecord value = context.getValue();
    final DirectBuffer currentActivityId = value.getActivityId();

    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    final ExecutableFlowElement flowElement = workflow.getElementById(currentActivityId);
    context.setElement(flowElement);
  }

  private void populateElementInstancesInContext() {
    final WorkflowInstanceRecord value = context.getValue();

    final ElementInstance elementInstance =
        elementInstances.getInstance(context.getRecord().getKey());
    final ElementInstance flowScopeInstance =
        elementInstances.getInstance(value.getScopeInstanceKey());

    context.setElementInstance(elementInstance);
    context.setFlowScopeInstance(flowScopeInstance);
  }

  private boolean shallProcessRecord() {

    if (context.getElementInstance() == null && context.getFlowScopeInstance() == null) {
      // do not process records when their instance has already finished/cancelled
      return false;
    }

    final Predicate<BpmnStepContext> guard = stepGuards.get(context.getState());
    return guard.test(context);
  }
}
