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

import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedCommandWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriterImpl;
import io.zeebe.broker.workflow.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.function.Consumer;

public class BpmnStepContext<T extends ExecutableFlowElement> {

  private TypedRecord<WorkflowInstanceRecord> record;
  private ExecutableWorkflow workflow;
  private ExecutableFlowElement element;
  private TypedCommandWriter commandWriter;
  private final EventOutput eventOutput;
  private Consumer<SideEffectProducer> sideEffect;

  private ElementInstance flowScopeInstance;
  private ElementInstance elementInstance;

  private final IncidentRecord incidentCommand = new IncidentRecord();
  private final MsgPackMergeTool mergeTool;

  public BpmnStepContext(EventOutput eventOutput) {
    this.eventOutput = eventOutput;
    this.mergeTool = new MsgPackMergeTool(4096);
  }

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public WorkflowInstanceRecord getValue() {
    return record.getValue();
  }

  public WorkflowInstanceIntent getState() {
    return (WorkflowInstanceIntent) record.getMetadata().getIntent();
  }

  public void setRecord(TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
  }

  public T getElement() {
    return (T) element;
  }

  public void setElement(ExecutableFlowElement element) {
    this.element = element;
  }

  public ExecutableWorkflow getWorkflow() {
    return workflow;
  }

  public void setWorkflow(ExecutableWorkflow workflow) {
    this.workflow = workflow;
  }

  public EventOutput getOutput() {
    return eventOutput;
  }

  public void setStreamWriter(TypedStreamWriter streamWriter) {
    this.eventOutput.setStreamWriter(streamWriter);
    this.commandWriter = streamWriter;
  }

  public TypedCommandWriter getCommandWriter() {
    return commandWriter;
  }

  public ElementInstance getFlowScopeInstance() {
    return flowScopeInstance;
  }

  public void setFlowScopeInstance(ElementInstance flowScopeInstance) {
    this.flowScopeInstance = flowScopeInstance;
  }

  /**
   * can be null
   *
   * @return
   */
  public ElementInstance getElementInstance() {
    return elementInstance;
  }

  public void setElementInstance(ElementInstance elementInstance) {
    this.elementInstance = elementInstance;
  }

  public void setSideEffect(Consumer<SideEffectProducer> sideEffect) {
    this.sideEffect = sideEffect;
  }

  public Consumer<SideEffectProducer> getSideEffect() {
    return sideEffect;
  }

  public MsgPackMergeTool getMergeTool() {
    return mergeTool;
  }

  public void raiseIncident(ErrorType errorType, String errorMessage) {

    incidentCommand.reset();

    incidentCommand
        .initFromWorkflowInstanceFailure(record)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage);

    eventOutput.storeFailedToken(record);

    if (!record.getMetadata().hasIncidentKey()) {
      commandWriter.writeNewCommand(IncidentIntent.CREATE, incidentCommand);
    } else {
      // TODO: casting is ok for the moment; the problem is rather that we
      // write an event (not command) for a different stream processor
      // => https://github.com/zeebe-io/zeebe/issues/1033
      ((TypedStreamWriterImpl) commandWriter)
          .writeFollowUpEvent(
              record.getMetadata().getIncidentKey(),
              IncidentIntent.RESOLVE_FAILED,
              incidentCommand);
    }
  }
}
