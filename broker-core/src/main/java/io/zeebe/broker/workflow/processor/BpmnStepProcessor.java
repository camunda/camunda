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
import io.zeebe.broker.workflow.index.WorkflowEngineState;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BpmnStepProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowEngineState state;
  private final BpmnStepHandlers stepHandlers;
  private final BpmnStepGuards stepGuards;

  private final WorkflowCache workflowCache;

  private BpmnStepContext context;

  public BpmnStepProcessor(
      ElementInstanceIndex scopeInstances,
      WorkflowCache workflowCache,
      SubscriptionCommandSender subscriptionCommandSender,
      WorkflowInstanceSubscriptionDataStore subscriptionStore) {

    this.workflowCache = workflowCache;
    this.stepHandlers = new BpmnStepHandlers(subscriptionCommandSender, subscriptionStore);
    this.stepGuards = new BpmnStepGuards();
    this.state = new WorkflowEngineState(scopeInstances);
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    state.onOpen(streamProcessor);
    this.context = new BpmnStepContext<>(state);
  }

  @Override
  public void onClose() {
    state.onClose();
  }

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {

    populateEventContext(record, streamWriter, sideEffect);
    if (stepGuards.shouldHandle(context)) {
      state.onEventConsumed(record);
      stepHandlers.handle(context);
    }
  }

  private void populateEventContext(
      TypedRecord<WorkflowInstanceRecord> record,
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
      populateElementInContext(deployedWorkflow);
      populateElementInstancesInContext();
    }
  }

  private void populateElementInContext(final DeployedWorkflow deployedWorkflow) {
    final WorkflowInstanceRecord value = context.getValue();
    final DirectBuffer currentActivityId = value.getActivityId();

    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    final ExecutableFlowElement flowElement = workflow.getElementById(currentActivityId);
    context.setElement(flowElement);
  }

  private void populateElementInstancesInContext() {
    final WorkflowInstanceRecord value = context.getValue();

    final ElementInstance elementInstance = state.getElementInstance(context.getRecord().getKey());
    final ElementInstance flowScopeInstance = state.getElementInstance(value.getScopeInstanceKey());

    context.setElementInstance(elementInstance);
    context.setFlowScopeInstance(flowScopeInstance);
  }
}
