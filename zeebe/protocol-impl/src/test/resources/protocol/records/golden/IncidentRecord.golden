/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ArrayValue;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

public final class IncidentRecord extends UnifiedRecordValue implements IncidentRecordValue {
  // Static StringValue keys to avoid memory waste
  private static final StringValue ERROR_TYPE_KEY = new StringValue("errorType");
  private static final StringValue ERROR_MESSAGE_KEY = new StringValue("errorMessage");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue ELEMENT_ID_KEY = new StringValue("elementId");
  private static final StringValue ELEMENT_INSTANCE_KEY_KEY = new StringValue("elementInstanceKey");
  private static final StringValue JOB_KEY_KEY = new StringValue("jobKey");
  private static final StringValue VARIABLE_SCOPE_KEY_KEY = new StringValue("variableScopeKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue ELEMENT_INSTANCE_PATH_KEY =
      new StringValue("elementInstancePath");
  private static final StringValue PROCESS_DEFINITION_PATH_KEY =
      new StringValue("processDefinitionPath");
  private static final StringValue CALLING_ELEMENT_PATH_KEY = new StringValue("callingElementPath");

  private final EnumProperty<ErrorType> errorTypeProp =
      new EnumProperty<>(ERROR_TYPE_KEY, ErrorType.class, ErrorType.UNKNOWN);
  private final StringProperty errorMessageProp = new StringProperty(ERROR_MESSAGE_KEY, "");
  private final StringProperty bpmnProcessIdProp = new StringProperty(BPMN_PROCESS_ID_KEY, "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);
  private final StringProperty elementIdProp = new StringProperty(ELEMENT_ID_KEY, "");
  private final LongProperty elementInstanceKeyProp =
      new LongProperty(ELEMENT_INSTANCE_KEY_KEY, -1L);
  private final LongProperty jobKeyProp = new LongProperty(JOB_KEY_KEY, -1L);
  private final LongProperty variableScopeKeyProp = new LongProperty(VARIABLE_SCOPE_KEY_KEY, -1L);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final ArrayProperty<ArrayValue<LongValue>> elementInstancePathProp =
      new ArrayProperty<>(ELEMENT_INSTANCE_PATH_KEY, () -> new ArrayValue<>(LongValue::new));
  private final ArrayProperty<LongValue> processDefinitionPathProp =
      new ArrayProperty<>(PROCESS_DEFINITION_PATH_KEY, LongValue::new);
  private final ArrayProperty<IntegerValue> callingElementPathProp =
      new ArrayProperty<>(CALLING_ELEMENT_PATH_KEY, IntegerValue::new);

  public IncidentRecord() {
    super(13);
    declareProperty(errorTypeProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(jobKeyProp)
        .declareProperty(variableScopeKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(elementInstancePathProp)
        .declareProperty(processDefinitionPathProp)
        .declareProperty(callingElementPathProp);
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
    tenantIdProp.setValue(record.getTenantId());
    setElementInstancePath(record.getElementInstancePath());
    setProcessDefinitionPath(record.getProcessDefinitionPath());
    setCallingElementPath(record.getCallingElementPath());
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

  @Override
  public List<List<Long>> getElementInstancePath() {
    final var elementInstancePath = new ArrayList<List<Long>>();
    elementInstancePathProp.forEach(
        pe -> {
          final var pathEntry = new ArrayList<Long>();
          pe.forEach(e -> pathEntry.add(e.getValue()));
          elementInstancePath.add(pathEntry);
        });
    return elementInstancePath;
  }

  public IncidentRecord setElementInstancePath(final List<List<Long>> elementInstancePath) {
    elementInstancePathProp.reset();
    elementInstancePath.forEach(
        pathEntry -> {
          final var entry = elementInstancePathProp.add();
          pathEntry.forEach(element -> entry.add().setValue(element));
        });
    return this;
  }

  @Override
  public List<Long> getProcessDefinitionPath() {
    final var processDefinitionPath = new ArrayList<Long>();
    processDefinitionPathProp.forEach(e -> processDefinitionPath.add(e.getValue()));
    return processDefinitionPath;
  }

  public IncidentRecord setProcessDefinitionPath(final List<Long> processDefinitionPath) {
    processDefinitionPathProp.reset();
    processDefinitionPath.forEach(e -> processDefinitionPathProp.add().setValue(e));
    return this;
  }

  @Override
  public List<Integer> getCallingElementPath() {
    final var callingElementPath = new ArrayList<Integer>();
    callingElementPathProp.forEach(e -> callingElementPath.add(e.getValue()));
    return callingElementPath;
  }

  public IncidentRecord setCallingElementPath(final List<Integer> callingElementPath) {
    callingElementPathProp.reset();
    callingElementPath.forEach(e -> callingElementPathProp.add().setValue(e));
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    // The root process instance key is the first element of the first list in the
    // elementInstancePath
    final var iterator = elementInstancePathProp.iterator();
    if (iterator.hasNext()) {
      final var firstList = iterator.next();
      final var listIterator = firstList.iterator();
      if (listIterator.hasNext()) {
        return listIterator.next().getValue();
      }
    }
    return -1L;
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

  public IncidentRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
