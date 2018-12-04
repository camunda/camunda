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
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.model.element.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.ElementInstanceState;
import io.zeebe.broker.workflow.state.WorkflowEngineState;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BpmnStepProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowEngineState state;
  private final BpmnStepHandlers stepHandlers;
  private final BpmnStepGuards stepGuards;
  private final WorkflowState workflowState;
  private final BpmnStepContext context;

  private ElementInstanceState elementInstanceState;

  public BpmnStepProcessor(
      WorkflowEngineState state, ZeebeState zeebeState, CatchEventOutput catchEventOutput) {
    this.state = state;
    this.workflowState = state.getWorkflowState();
    this.stepHandlers = new BpmnStepHandlers(workflowState, zeebeState);
    this.stepGuards = new BpmnStepGuards();

    final EventOutput eventOutput = new EventOutput(state);
    this.context = new BpmnStepContext<>(eventOutput, catchEventOutput);
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    this.elementInstanceState = workflowState.getElementInstanceState();
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
      elementInstanceState.flushDirtyState();
    }
  }

  private void populateEventContext(
      TypedRecord<WorkflowInstanceRecord> record,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {

    context.setRecord(record);
    context.setStreamWriter(streamWriter);

    context.getSideEffect().clear();
    sideEffect.accept(context.getSideEffect());

    final long workflowKey = record.getValue().getWorkflowKey();
    final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(workflowKey);

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
    final DirectBuffer currentElementId = value.getElementId();

    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    final ExecutableFlowElement flowElement = workflow.getElementById(currentElementId);
    context.setElement(flowElement);
  }

  private void populateElementInstancesInContext() {
    final WorkflowInstanceRecord value = context.getValue();

    final ElementInstance elementInstance =
        elementInstanceState.getInstance(context.getRecord().getKey());
    final ElementInstance flowScopeInstance =
        elementInstanceState.getInstance(value.getScopeInstanceKey());

    context.setElementInstance(elementInstance);
    context.setFlowScopeInstance(flowScopeInstance);
  }
}
