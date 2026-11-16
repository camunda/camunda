/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("java:S2160")
@MsgpackPojo
@NullMarked
public final class ProcessEventRecord extends MsgpackBufferValue<ProcessEventRecord>
    implements MsgpackRecordValue, ProcessEventRecordValue {

  final RequiredLong scopeKey = new RequiredLong();
  @Nullable DirectBuffer targetElementId;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ProcessEventRecord wrap(final ProcessEventRecord record) {
    scopeKey.set(record.getScopeKey());
    targetElementId = record.getTargetElementIdBuffer();
    variables = Decoding.document(record.getVariablesBuffer());
    processDefinitionKey = record.getProcessDefinitionKey();
    processInstanceKey = record.getProcessInstanceKey();
    tenantId = BufferUtil.wrapString(record.getTenantId());
    storageOrdinal = record.getStorageOrdinal();

    return this;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return Decoding.required(targetElementId, "targetElementId");
  }

  public ProcessEventRecord setTargetElementIdBuffer(final DirectBuffer targetElementIdBuffer) {
    targetElementId = targetElementIdBuffer;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  public ProcessEventRecord setVariablesBuffer(final DirectBuffer variablesBuffer) {
    variables = Decoding.document(variablesBuffer);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(getVariablesBuffer());
  }

  @Override
  public long getScopeKey() {
    return scopeKey.get();
  }

  public ProcessEventRecord setScopeKey(final long scopeKey) {
    this.scopeKey.set(scopeKey);
    return this;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(getTargetElementIdBuffer());
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessEventRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ProcessEventRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProcessEventRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessEventRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessEventRecord> codec() {
    return ProcessEventRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessEventRecord> ops() {
    return ProcessEventRecordOps.INSTANCE;
  }
}
