/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class IncidentRecord extends UnifiedRecordValue implements IncidentRecordValue {
  private final EnumProperty<ErrorType> errorTypeProp =
      new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
  private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final LongProperty variableScopeKeyProp = new LongProperty("variableScopeKey", -1L);

  public IncidentRecord() {
    declareProperty(errorTypeProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp)
        .declareProperty(variableScopeKeyProp);
  }

  public void wrap(final IncidentRecord record) {
    errorTypeProp.setValue(record.getErrorType());
    errorMessageProp.setValue(record.getErrorMessage());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    elementIdProp.setValue(record.getElementIdBuffer());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
    jobKeyProp.setValue(record.getJobKey());
    variableScopeKeyProp.setValue(record.getVariableScopeKey());
  }

  public IncidentRecord initFromProcessInstanceFailure(
      final long key, final ProcessInstanceRecord processInstanceEvent) {

    setElementInstanceKey(key);
    setBpmnProcessId(processInstanceEvent.getBpmnProcessIdBuffer());
    setProcessDefinitionKey(processInstanceEvent.getProcessDefinitionKey());
    setProcessInstanceKey(processInstanceEvent.getProcessInstanceKey());
    setElementId(processInstanceEvent.getElementIdBuffer());
    setVariableScopeKey(key);

    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getErrorMessageBuffer() {
    return errorMessageProp.getValue();
  }

  @Override
  public ErrorType getErrorType() {
    return errorTypeProp.getValue();
  }

  @Override
  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessageProp.getValue());
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public IncidentRecord setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public IncidentRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public IncidentRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId, 0, elementId.capacity());
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public IncidentRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public long getVariableScopeKey() {
    return variableScopeKeyProp.getValue();
  }

  public IncidentRecord setVariableScopeKey(final long variableScopeKey) {
    variableScopeKeyProp.setValue(variableScopeKey);
    return this;
  }

  public IncidentRecord setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
    return this;
  }

  public IncidentRecord setErrorMessage(final DirectBuffer errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  public IncidentRecord setErrorMessage(final String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  public IncidentRecord setErrorType(final ErrorType errorType) {
    errorTypeProp.setValue(errorType);
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public IncidentRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }
}
