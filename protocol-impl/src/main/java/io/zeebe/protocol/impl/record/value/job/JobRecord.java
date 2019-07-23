/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.job;

import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord.PROP_WORKFLOW_INSTANCE_KEY;

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
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobRecord extends UnifiedRecordValue implements JobRecordValue {
  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);
  private static final String EMPTY_STRING = "";
  private static final String RETRIES = "retries";
  private static final String TYPE = "type";
  private static final String CUSTOM_HEADERS = "customHeaders";
  private static final String VARIABLES = "variables";
  private static final String ERROR_MESSAGE = "errorMessage";
  private final LongProperty deadlineProp = new LongProperty("deadline", -1);
  private final StringProperty workerProp = new StringProperty("worker", EMPTY_STRING);
  private final IntegerProperty retriesProp = new IntegerProperty(RETRIES, -1);
  private final StringProperty typeProp = new StringProperty(TYPE, EMPTY_STRING);
  private final PackedProperty customHeadersProp = new PackedProperty(CUSTOM_HEADERS, NO_HEADERS);
  private final DocumentProperty variableProp = new DocumentProperty(VARIABLES);
  private final StringProperty errorMessageProp = new StringProperty(ERROR_MESSAGE, "");

  private final LongProperty workflowInstanceKeyProp =
      new LongProperty(PROP_WORKFLOW_INSTANCE_KEY, -1L);
  private final StringProperty bpmnProcessIdProp =
      new StringProperty(PROP_WORKFLOW_BPMN_PROCESS_ID, EMPTY_STRING);
  private final IntegerProperty workflowDefinitionVersionProp =
      new IntegerProperty("workflowDefinitionVersion", -1);
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1L);
  private final StringProperty elementIdProp = new StringProperty("elementId", EMPTY_STRING);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);

  public JobRecord() {
    this.declareProperty(deadlineProp)
        .declareProperty(workerProp)
        .declareProperty(retriesProp)
        .declareProperty(typeProp)
        .declareProperty(customHeadersProp)
        .declareProperty(variableProp)
        .declareProperty(errorMessageProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(workflowDefinitionVersionProp)
        .declareProperty(workflowKeyProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementInstanceKeyProp);
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

  @Override
  public String getType() {
    return BufferUtil.bufferAsString(typeProp.getValue());
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return MsgPackConverter.convertToStringMap(customHeadersProp.getValue());
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
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
    return BufferUtil.bufferAsString(errorMessageProp.getValue());
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersionProp.getValue();
  }

  @Override
  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public JobRecord setWorkflowKey(long workflowKey) {
    this.workflowKeyProp.setValue(workflowKey);
    return this;
  }

  public JobRecord setWorkflowDefinitionVersion(int version) {
    this.workflowDefinitionVersionProp.setValue(version);
    return this;
  }

  public JobRecord setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public JobRecord setBpmnProcessId(DirectBuffer bpmnProcessId) {
    this.bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public JobRecord setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKeyProp.setValue(elementInstanceKey);
    return this;
  }

  public JobRecord setElementId(String elementId) {
    this.elementIdProp.setValue(elementId);
    return this;
  }

  public JobRecord setElementId(DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public JobRecord setErrorMessage(String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }

  public JobRecord setErrorMessage(DirectBuffer buf) {
    return setErrorMessage(buf, 0, buf.capacity());
  }

  public JobRecord setDeadline(long val) {
    deadlineProp.setValue(val);
    return this;
  }

  public JobRecord setRetries(int retries) {
    retriesProp.setValue(retries);
    return this;
  }

  public JobRecord setWorker(String worker) {
    this.workerProp.setValue(worker);
    return this;
  }

  public JobRecord setWorker(DirectBuffer worker) {
    return setWorker(worker, 0, worker.capacity());
  }

  public JobRecord setCustomHeaders(DirectBuffer buffer) {
    customHeadersProp.setValue(buffer, 0, buffer.capacity());
    return this;
  }

  public JobRecord setType(String type) {
    this.typeProp.setValue(type);
    return this;
  }

  public JobRecord setType(DirectBuffer buf) {
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

  public JobRecord setVariables(DirectBuffer variables) {
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
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public JobRecord setWorkflowInstanceKey(long key) {
    this.workflowInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }

  public JobRecord setElementId(DirectBuffer activityId, int offset, int length) {
    this.elementIdProp.setValue(activityId, offset, length);
    return this;
  }

  public JobRecord setErrorMessage(DirectBuffer buf, int offset, int length) {
    errorMessageProp.setValue(buf, offset, length);
    return this;
  }

  public JobRecord setType(DirectBuffer buf, int offset, int length) {
    typeProp.setValue(buf, offset, length);
    return this;
  }

  public JobRecord setWorker(DirectBuffer worker, int offset, int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }
}
