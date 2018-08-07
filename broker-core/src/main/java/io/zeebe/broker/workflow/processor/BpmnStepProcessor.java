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
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.ActivityInstanceMap;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.PayloadCache;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex.WorkflowInstance;
import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.processor.activity.CreateJobHandler;
import io.zeebe.broker.workflow.processor.activity.InputMappingHandler;
import io.zeebe.broker.workflow.processor.activity.OutputMappingHandler;
import io.zeebe.broker.workflow.processor.exclusivegw.ExclusiveSplitHandler;
import io.zeebe.broker.workflow.processor.flownode.ConsumeTokenHandler;
import io.zeebe.broker.workflow.processor.flownode.TakeSequenceFlowHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.ActivateGatewayHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.EnterIntermediateEventHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.StartActivityHandler;
import io.zeebe.broker.workflow.processor.sequenceflow.TriggerEndEventHandler;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.transport.ClientResponse;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BpmnStepProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowInstanceIndex workflowInstanceIndex;
  private final WorkflowCache workflowCache;
  private final BpmnStepContext context = new BpmnStepContext<>();
  private final Map<BpmnStep, BpmnStepHandler> stepHandlers = new EnumMap<>(BpmnStep.class);

  private ActorControl actor;

  public BpmnStepProcessor(
      WorkflowCache workflowCache,
      WorkflowInstanceIndex workflowInstanceIndex,
      ActivityInstanceMap activityInstanceMap,
      PayloadCache payloadCache) {

    this.workflowInstanceIndex = workflowInstanceIndex;
    this.workflowCache = workflowCache;

    // activity
    stepHandlers.put(BpmnStep.CREATE_JOB, new CreateJobHandler());
    stepHandlers.put(
        BpmnStep.APPLY_INPUT_MAPPING,
        new InputMappingHandler(payloadCache, workflowInstanceIndex, activityInstanceMap));
    stepHandlers.put(
        BpmnStep.APPLY_OUTPUT_MAPPING,
        new OutputMappingHandler(payloadCache, workflowInstanceIndex, activityInstanceMap));

    // exclusive gateway
    stepHandlers.put(BpmnStep.EXCLUSIVE_SPLIT, new ExclusiveSplitHandler());

    // flow node
    stepHandlers.put(
        BpmnStep.CONSUME_TOKEN, new ConsumeTokenHandler(payloadCache, workflowInstanceIndex));
    stepHandlers.put(BpmnStep.TAKE_SEQUENCE_FLOW, new TakeSequenceFlowHandler());

    // sequence flow
    stepHandlers.put(BpmnStep.ACTIVATE_GATEWAY, new ActivateGatewayHandler());
    stepHandlers.put(BpmnStep.ENTER_INTERMEDIATE_EVENT, new EnterIntermediateEventHandler());
    stepHandlers.put(BpmnStep.START_ACTIVITY, new StartActivityHandler());
    stepHandlers.put(BpmnStep.TRIGGER_END_EVENT, new TriggerEndEventHandler());
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    this.actor = streamProcessor.getActor();
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {

    context.setRecord(record);
    context.setStreamWriter(streamWriter);

    if (!isWorkflowInstanceActive(record)) {
      // do not process records when their instance has already finished/cancelled
      return;
    }

    final long workflowKey = record.getValue().getWorkflowKey();
    final DeployedWorkflow deployedWorkflow = workflowCache.getWorkflowByKey(workflowKey);

    if (deployedWorkflow == null) {
      fetchWorkflow(workflowKey, this::resolveCurrentFlowNode, ctx);
    } else {
      resolveCurrentFlowNode(deployedWorkflow);
    }
  }

  private boolean isWorkflowInstanceActive(TypedRecord<WorkflowInstanceRecord> record) {
    final long workflowInstanceKey = record.getValue().getWorkflowInstanceKey();

    final WorkflowInstance workflowInstance = workflowInstanceIndex.get(workflowInstanceKey);
    return workflowInstance != null && workflowInstance.getTokenCount() > 0;
  }

  private void resolveCurrentFlowNode(DeployedWorkflow deployedWorkflow) {
    final DirectBuffer currentActivityId = context.getValue().getActivityId();

    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    final ExecutableFlowElement flowElement = workflow.getElementById(currentActivityId);

    context.setElement(flowElement);

    final BpmnStep step =
        flowElement.getStep((WorkflowInstanceIntent) context.getRecord().getMetadata().getIntent());

    if (step != null) {
      final BpmnStepHandler stepHandler = stepHandlers.get(step);
      if (stepHandler != null) {
        stepHandler.handle(context);
      }
    }
  }

  private void fetchWorkflow(
      long workflowKey, Consumer<DeployedWorkflow> onFetched, EventLifecycleContext ctx) {
    final ActorFuture<ClientResponse> responseFuture =
        workflowCache.fetchWorkflowByKey(workflowKey);
    final ActorFuture<Void> onCompleted = new CompletableActorFuture<>();

    ctx.async(onCompleted);

    actor.runOnCompletion(
        responseFuture,
        (response, err) -> {
          if (err != null) {
            onCompleted.completeExceptionally(
                new RuntimeException("Could not fetch workflow", err));
          } else {
            try {
              final DeployedWorkflow workflow =
                  workflowCache.addWorkflow(response.getResponseBuffer());

              onFetched.accept(workflow);

              onCompleted.complete(null);
            } catch (Exception e) {
              onCompleted.completeExceptionally(
                  new RuntimeException("Error while processing fetched workflow", e));
            }
          }
        });
  }
}
