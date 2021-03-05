/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.job;

import static io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord.PROP_PROCESS_INSTANCE_KEY;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.PackedProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class JobRecord extends UnifiedRecordValue implements JobRecordValue {
  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);
  private static final String EMPTY_STRING = "";
  private static final String RETRIES = "retries";
  private static final String TYPE = "type";
  private static final String CUSTOM_HEADERS = "customHeaders";
  private static final String VARIABLES = "variables";
  private static final String ERROR_MESSAGE = "errorMessage";

  private final StringProperty typeProp = new StringProperty(TYPE, EMPTY_STRING);

  private final StringProperty workerProp = new StringProperty("worker", EMPTY_STRING);
  private final LongProperty deadlineProp = new LongProperty("deadline", -1);
  private final IntegerProperty retriesProp = new IntegerProperty(RETRIES, -1);

  private final PackedProperty customHeadersProp = new PackedProperty(CUSTOM_HEADERS, NO_HEADERS);
  private final DocumentProperty variableProp = new DocumentProperty(VARIABLES);

  private final StringProperty errorMessageProp = new StringProperty(ERROR_MESSAGE, EMPTY_STRING);
  private final StringProperty errorCodeProp = new StringProperty("errorCode", EMPTY_STRING);

  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROP_PROCESS_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_PROCESS_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);

  public JobRecord() {
    declareProperty(deadlineProp)
        .declareProperty(workerProp)
        .declareProperty(retriesProp)
        .declareProperty(typeProp)
        .declareProperty(customHeadersProp)
        .declareProperty(variableProp)
        .declareProperty(errorMessageProp)
        .declareProperty(errorCodeProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp);
  }

  public void wrapWithoutVariables(final JobRecord record) {
    deadlineProp.setValue(record.getDeadline());
    workerProp.setValue(record.getWorkerBuffer());
    retriesProp.setValue(record.getRetries());
    typeProp.setValue(record.getTypeBuffer());
    final DirectBuffer customHeaders = record.getCustomHeadersBuffer();
    customHeadersProp.setValue(customHeaders, 0, customHeaders.capacity());
    errorMessageProp.setValue(record.getErrorMessageBuffer());
    errorCodeProp.setValue(record.getErrorCodeBuffer());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    processDefinitionVersionProp.setValue(record.getProcessDefinitionVersion());
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    processInstanceKeyProp.setValue(record.getProcessInstanceKey());
    elementIdProp.setValue(record.getElementIdBuffer());
    elementInstanceKeyProp.setValue(record.getElementInstanceKey());
  }

  public JobRecord resetVariables() {
    variableProp.reset();
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCustomHeadersBuffer() {
    return customHeadersProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getErrorMessageBuffer() {
    return errorMessageProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getErrorCodeBuffer() {
    return errorCodeProp.getValue();
  }

  @Override
  public String getType() {
    return bufferAsString(typeProp.getValue());
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return MsgPackConverter.convertToStringMap(customHeadersProp.getValue());
  }

  @Override
  public String getWorker() {
    return bufferAsString(workerProp.getValue());
  }

  @Override
  public int getRetries() {
    return retriesProp.getValue();
  }

  @Override
  public long getDeadline() {
    return deadlineProp.getValue();
  }

  @Override
  public String getErrorMessage() {
    return bufferAsString(errorMessageProp.getValue());
  }

  @Override
  public String getErrorCode() {
    return bufferAsString(errorCodeProp.getValue());
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public JobRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  public JobRecord setProcessDefinitionVersion(final int version) {
    processDefinitionVersionProp.setValue(version);
    return this;
  }

  public JobRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public JobRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public JobRecord setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public JobRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public JobRecord setElementId(final DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public JobRecord setErrorCode(final DirectBuffer errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public JobRecord setErrorMessage(final String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  public JobRecord setErrorMessage(final DirectBuffer buf) {
    return setErrorMessage(buf, 0, buf.capacity());
  }

  public JobRecord setDeadline(final long val) {
    deadlineProp.setValue(val);
    return this;
  }

  public JobRecord setRetries(final int retries) {
    retriesProp.setValue(retries);
    return this;
  }

  public JobRecord setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobRecord setWorker(final DirectBuffer worker) {
    return setWorker(worker, 0, worker.capacity());
  }

  public JobRecord setCustomHeaders(final DirectBuffer buffer) {
    customHeadersProp.setValue(buffer, 0, buffer.capacity());
    return this;
  }

  public JobRecord setType(final String type) {
    typeProp.setValue(type);
    return this;
  }

  public JobRecord setType(final DirectBuffer buf) {
    return setType(buf, 0, buf.capacity());
  }

  @JsonIgnore
  public DirectBuffer getTypeBuffer() {
    return typeProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variableProp.getValue());
  }

  public JobRecord setVariables(final DirectBuffer variables) {
    variableProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variableProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public JobRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  public JobRecord setElementId(final DirectBuffer activityId, final int offset, final int length) {
    elementIdProp.setValue(activityId, offset, length);
    return this;
  }

  public JobRecord setErrorMessage(final DirectBuffer buf, final int offset, final int length) {
    errorMessageProp.setValue(buf, offset, length);
    return this;
  }

  public JobRecord setType(final DirectBuffer buf, final int offset, final int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public JobRecord setWorker(final DirectBuffer worker, final int offset, final int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }
}
