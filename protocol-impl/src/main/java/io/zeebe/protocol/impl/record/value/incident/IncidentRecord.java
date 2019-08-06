/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class IncidentRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, IncidentRecordValue {
  private final EnumProperty<ErrorType> errorTypeProp =
      new EnumProperty<>("errorType", ErrorType.class, ErrorType.UNKNOWN);
  private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1L);
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", -1L);
  private final LongProperty variableScopeKeyProp = new LongProperty("variableScopeKey", -1L);

  public IncidentRecord() {
    this.declareProperty(errorTypeProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp)
        .declareProperty(variableScopeKeyProp);
  }

  public IncidentRecord initFromWorkflowInstanceFailure(
      long key, WorkflowInstanceRecord workflowInstanceEvent) {

    setElementInstanceKey(key);
    setBpmnProcessId(workflowInstanceEvent.getBpmnProcessIdBuffer());
    setWorkflowKey(workflowInstanceEvent.getWorkflowKey());
    setWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey());
    setElementId(workflowInstanceEvent.getElementIdBuffer());
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

  public IncidentRecord setBpmnProcessId(DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer, 0, directBuffer.capacity());
    return this;
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public IncidentRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public IncidentRecord setElementId(DirectBuffer elementId) {
    this.elementIdProp.setValue(elementId, 0, elementId.capacity());
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public IncidentRecord setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public long getVariableScopeKey() {
    return variableScopeKeyProp.getValue();
  }

  public IncidentRecord setVariableScopeKey(long variableScopeKey) {
    this.variableScopeKeyProp.setValue(variableScopeKey);
    return this;
  }

  public IncidentRecord setJobKey(long jobKey) {
    this.jobKeyProp.setValue(jobKey);
    return this;
  }

  public IncidentRecord setErrorMessage(DirectBuffer errorMessage) {
    this.errorMessageProp.setValue(errorMessage);
    return this;
  }

  public IncidentRecord setErrorMessage(String errorMessage) {
    this.errorMessageProp.setValue(errorMessage);
    return this;
  }

  public IncidentRecord setErrorType(ErrorType errorType) {
    this.errorTypeProp.setValue(errorType);
    return this;
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public IncidentRecord setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKeyProp.setValue(workflowInstanceKey);
    return this;
  }
}
