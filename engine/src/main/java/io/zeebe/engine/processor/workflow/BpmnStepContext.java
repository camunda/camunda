/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedCommandWriter;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElement;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.function.Consumer;

public final class BpmnStepContext<T extends ExecutableFlowElement> {

  private final IncidentRecord incidentCommand = new IncidentRecord();
  private final SideEffectQueue sideEffect = new SideEffectQueue();

  private final EventOutput eventOutput;
  private final WorkflowState stateDb;

  private long key;
  private WorkflowInstanceIntent intent;
  private WorkflowInstanceRecord recordValue;

  private ExecutableFlowElement element;
  private TypedCommandWriter commandWriter;

  private TypedRecord<WorkflowInstanceRecord> record;
  private Consumer<SideEffectProducer> sideEffectConsumer;

  public BpmnStepContext(final WorkflowState stateDb, final EventOutput eventOutput) {
    this.stateDb = stateDb;
    this.eventOutput = eventOutput;
  }

  public WorkflowInstanceRecord getValue() {
    return recordValue;
  }

  public WorkflowInstanceIntent getState() {
    return intent;
  }

  public long getKey() {
    return key;
  }

  public void init(
      final long key,
      final WorkflowInstanceRecord recordValue,
      final WorkflowInstanceIntent intent) {
    this.recordValue = recordValue;
    this.key = key;
    this.intent = intent;
  }

  public T getElement() {
    return (T) element;
  }

  public void setElement(final ExecutableFlowElement element) {
    this.element = element;
  }

  public EventOutput getOutput() {
    return eventOutput;
  }

  public void setStreamWriter(final TypedStreamWriter streamWriter) {
    eventOutput.setStreamWriter(streamWriter);
    commandWriter = streamWriter;
  }

  public void setResponseWriter(final TypedResponseWriter responseWriter) {
    eventOutput.setResponseWriter(responseWriter);
  }

  public TypedCommandWriter getCommandWriter() {
    return commandWriter;
  }

  public ElementInstance getFlowScopeInstance() {
    final WorkflowInstanceRecord value = getValue();
    return stateDb.getElementInstanceState().getInstance(value.getFlowScopeKey());
  }

  /**
   * can be null
   *
   * @return
   */
  public ElementInstance getElementInstance() {
    if (recordValue != null) {
      return stateDb.getElementInstanceState().getInstance(key);
    }
    return null;
  }

  public SideEffectQueue getSideEffect() {
    return sideEffect;
  }

  public void raiseIncident(final ErrorType errorType, final String errorMessage) {
    raiseIncident(errorType, key, errorMessage);
  }

  public void raiseIncident(
      final ErrorType errorType, final long variableScopeKey, final String errorMessage) {
    incidentCommand.reset();

    incidentCommand
        .initFromWorkflowInstanceFailure(key, recordValue)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage)
        .setVariableScopeKey(variableScopeKey);

    eventOutput.storeFailedRecord(key, recordValue, intent);
    commandWriter.appendNewCommand(IncidentIntent.CREATE, incidentCommand);
  }

  public WorkflowState getStateDb() {
    return stateDb;
  }

  public ElementInstanceState getElementInstanceState() {
    return stateDb.getElementInstanceState();
  }

  public TypedRecord<WorkflowInstanceRecord> getRecord() {
    return record;
  }

  public void setRecord(final TypedRecord<WorkflowInstanceRecord> record) {
    this.record = record;
  }

  public Consumer<SideEffectProducer> getSideEffectConsumer() {
    return sideEffectConsumer;
  }

  public void setSideEffectConsumer(final Consumer<SideEffectProducer> sideEffect) {
    sideEffectConsumer = sideEffect;
  }
}
