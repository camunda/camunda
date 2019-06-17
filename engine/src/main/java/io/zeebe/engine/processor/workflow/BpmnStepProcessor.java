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

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.WorkflowEngineState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BpmnStepProcessor implements TypedRecordProcessor<WorkflowInstanceRecord> {

  private final WorkflowEngineState state;
  private final BpmnStepHandlers stepHandlers;
  private final WorkflowState workflowState;
  private final BpmnStepContext context;

  public BpmnStepProcessor(
      WorkflowEngineState state, ZeebeState zeebeState, CatchEventBehavior catchEventBehavior) {
    this.state = state;
    this.workflowState = state.getWorkflowState();
    this.stepHandlers = new BpmnStepHandlers(zeebeState, catchEventBehavior);

    final EventOutput eventOutput = new EventOutput(state, zeebeState.getKeyGenerator());
    this.context = new BpmnStepContext<>(workflowState, eventOutput);
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
    processRecordValue(
        record.getKey(),
        record.getValue(),
        (WorkflowInstanceIntent) record.getMetadata().getIntent(),
        streamWriter,
        sideEffect);
  }

  public void processRecordValue(
      long key,
      WorkflowInstanceRecord recordValue,
      WorkflowInstanceIntent intent,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    populateEventContext(key, recordValue, intent, streamWriter, sideEffect);
    stepHandlers.handle(context);
  }

  private void populateEventContext(
      long key,
      WorkflowInstanceRecord recordValue,
      WorkflowInstanceIntent intent,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {

    context.init(key, recordValue, intent);
    context.setStreamWriter(streamWriter);

    context.getSideEffect().clear();
    sideEffect.accept(context.getSideEffect());

    final long workflowKey = recordValue.getWorkflowKey();
    final DeployedWorkflow deployedWorkflow = workflowState.getWorkflowByKey(workflowKey);

    if (deployedWorkflow == null) {
      throw new IllegalStateException(
          "Error while processing workflow. Workflow with " + workflowKey + " is not deployed");
    } else {
      populateElementInContext(deployedWorkflow);
    }
  }

  private void populateElementInContext(final DeployedWorkflow deployedWorkflow) {
    final WorkflowInstanceRecord value = context.getValue();
    final DirectBuffer currentElementId = value.getElementIdBuffer();

    final ExecutableWorkflow workflow = deployedWorkflow.getWorkflow();
    final ExecutableFlowElement flowElement = workflow.getElementById(currentElementId);
    context.setElement(flowElement);
  }
}
