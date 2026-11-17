/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.incident;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class IncidentRecord extends MsgpackBufferValue<IncidentRecord>
    implements MsgpackRecordValue, IncidentRecordValue {

  ErrorType errorType = ErrorType.UNKNOWN;
  DirectBuffer errorMessage = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer elementId = Decoding.EMPTY;
  long elementInstanceKey = -1L;
  long jobKey = -1L;
  long variableScopeKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final List<List<Long>> elementInstancePath = new ArrayList<>();
  final List<Long> processDefinitionPath = new ArrayList<>();
  final IntArrayList callingElementPath = new IntArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final IncidentRecord record) {
    errorType = record.getErrorType();
    errorMessage = record.errorMessage;
    bpmnProcessId = record.bpmnProcessId;
    processDefinitionKey = record.getProcessDefinitionKey();
    processInstanceKey = record.getProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    elementId = record.elementId;
    elementInstanceKey = record.getElementInstanceKey();
    jobKey = record.getJobKey();
    variableScopeKey = record.getVariableScopeKey();
    tenantId = record.tenantId;
    setElementInstancePath(record.getElementInstancePath());
    setProcessDefinitionPath(record.getProcessDefinitionPath());
    setCallingElementPath(record.getCallingElementPath());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  @JsonIgnore
  public DirectBuffer getErrorMessageBuffer() {
    return errorMessage;
  }

  @Override
  public ErrorType getErrorType() {
    return errorType;
  }

  @Override
  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessage);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public IncidentRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public IncidentRecord setElementId(final DirectBuffer elementId) {
    this.elementId = elementId;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public IncidentRecord setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessId = directBuffer;
    return this;
  }

  @Override
  public long getJobKey() {
    return jobKey;
  }

  @Override
  public long getVariableScopeKey() {
    return variableScopeKey;
  }

  public IncidentRecord setVariableScopeKey(final long variableScopeKey) {
    this.variableScopeKey = variableScopeKey;
    return this;
  }

  @Override
  public List<List<Long>> getElementInstancePath() {
    final var copy = new ArrayList<List<Long>>(elementInstancePath.size());
    for (final var pathEntry : elementInstancePath) {
      copy.add(new ArrayList<>(pathEntry));
    }
    return copy;
  }

  public IncidentRecord setElementInstancePath(final List<List<Long>> elementInstancePath) {
    this.elementInstancePath.clear();
    for (final var pathEntry : elementInstancePath) {
      this.elementInstancePath.add(new ArrayList<>(pathEntry));
    }
    return this;
  }

  @Override
  public List<Long> getProcessDefinitionPath() {
    return new ArrayList<>(processDefinitionPath);
  }

  public IncidentRecord setProcessDefinitionPath(final List<Long> processDefinitionPath) {
    this.processDefinitionPath.clear();
    this.processDefinitionPath.addAll(processDefinitionPath);
    return this;
  }

  @Override
  public List<Integer> getCallingElementPath() {
    final var path = new ArrayList<Integer>(callingElementPath.size());
    for (int i = 0; i < callingElementPath.size(); i++) {
      path.add(callingElementPath.getInt(i));
    }
    return path;
  }

  public IncidentRecord setCallingElementPath(final List<Integer> callingElementPath) {
    this.callingElementPath.clear();
    callingElementPath.forEach(this.callingElementPath::addInt);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    // The root process instance key is the first element of the first list in the
    // elementInstancePath
    if (!elementInstancePath.isEmpty() && !elementInstancePath.get(0).isEmpty()) {
      return elementInstancePath.get(0).get(0);
    }
    return -1L;
  }

  public IncidentRecord setJobKey(final long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public IncidentRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public IncidentRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public IncidentRecord setErrorMessage(final DirectBuffer errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public IncidentRecord setErrorMessage(final String errorMessage) {
    this.errorMessage = BufferUtil.wrapString(errorMessage);
    return this;
  }

  public IncidentRecord setErrorType(final ErrorType errorType) {
    this.errorType = errorType;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public IncidentRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public IncidentRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<IncidentRecord> codec() {
    return IncidentRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<IncidentRecord> ops() {
    return IncidentRecordOps.INSTANCE;
  }
}
