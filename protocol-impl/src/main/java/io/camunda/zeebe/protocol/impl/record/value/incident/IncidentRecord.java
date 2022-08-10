/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public IncidentRecord() {
    declareProperty(errorTypeProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp)
        .declareProperty(variableScopeKeyProp)
        .declareProperty(tenantIdProp);
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
    tenantIdProp.setValue(record.getTenantIdBuffer());
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

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public IncidentRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public IncidentRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId, 0, elementId.capacity());
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public IncidentRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  @Override
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

  public IncidentRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
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

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public IncidentRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public IncidentRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }
}
