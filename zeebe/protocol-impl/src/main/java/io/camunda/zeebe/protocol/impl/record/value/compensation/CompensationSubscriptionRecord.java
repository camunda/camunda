/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.compensation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class CompensationSubscriptionRecord
    extends MsgpackBufferValue<CompensationSubscriptionRecord>
    implements MsgpackRecordValue, CompensationSubscriptionRecordValue {

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long processInstanceKey = -1L;
  long processDefinitionKey = -1L;
  DirectBuffer compensableActivityId = Decoding.EMPTY;
  DirectBuffer throwEventId = Decoding.EMPTY;
  long throwEventInstanceKey = -1L;
  DirectBuffer compensationHandlerId = Decoding.EMPTY;
  long compensationHandlerInstanceKey = -1L;
  long compensableActivityScopeKey = -1L;
  long compensableActivityInstanceKey = -1L;
  int storageOrdinal = 0;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final CompensationSubscriptionRecord record) {
    tenantId = BufferUtil.wrapString(record.getTenantId());
    processInstanceKey = record.getProcessInstanceKey();
    processDefinitionKey = record.getProcessDefinitionKey();
    compensableActivityId = BufferUtil.wrapString(record.getCompensableActivityId());
    throwEventId = BufferUtil.wrapString(record.getThrowEventId());
    throwEventInstanceKey = record.getThrowEventInstanceKey();
    compensationHandlerId = BufferUtil.wrapString(record.getCompensationHandlerId());
    compensationHandlerInstanceKey = record.getCompensationHandlerInstanceKey();
    compensableActivityScopeKey = record.getCompensableActivityScopeKey();
    compensableActivityInstanceKey = record.getCompensableActivityInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    variables = Decoding.document(record.getVariablesBuffer());
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public CompensationSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CompensationSubscriptionRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public CompensationSubscriptionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getCompensableActivityId() {
    return BufferUtil.bufferAsString(compensableActivityId);
  }

  @Override
  public String getThrowEventId() {
    return BufferUtil.bufferAsString(throwEventId);
  }

  @Override
  public long getThrowEventInstanceKey() {
    return throwEventInstanceKey;
  }

  @Override
  public String getCompensationHandlerId() {
    return BufferUtil.bufferAsString(compensationHandlerId);
  }

  @Override
  public long getCompensationHandlerInstanceKey() {
    return compensationHandlerInstanceKey;
  }

  @Override
  public long getCompensableActivityScopeKey() {
    return compensableActivityScopeKey;
  }

  @Override
  public long getCompensableActivityInstanceKey() {
    return compensableActivityInstanceKey;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public CompensationSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public CompensationSubscriptionRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityInstanceKey(
      final long compensableActivityInstanceKey) {
    this.compensableActivityInstanceKey = compensableActivityInstanceKey;
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityScopeKey(
      final long scopeInstanceKey) {
    compensableActivityScopeKey = scopeInstanceKey;
    return this;
  }

  public CompensationSubscriptionRecord setCompensationHandlerInstanceKey(
      final long compensationHandlerInstanceKey) {
    this.compensationHandlerInstanceKey = compensationHandlerInstanceKey;
    return this;
  }

  public CompensationSubscriptionRecord setCompensationHandlerId(
      final String compensationHandlerId) {
    this.compensationHandlerId = BufferUtil.wrapString(compensationHandlerId);
    return this;
  }

  public CompensationSubscriptionRecord setThrowEventInstanceKey(final long throwEventInstanceKey) {
    this.throwEventInstanceKey = throwEventInstanceKey;
    return this;
  }

  public CompensationSubscriptionRecord setThrowEventId(final String throwEventId) {
    this.throwEventId = BufferUtil.wrapString(throwEventId);
    return this;
  }

  public CompensationSubscriptionRecord setCompensableActivityId(
      final String compensableActivityId) {
    this.compensableActivityId = BufferUtil.wrapString(compensableActivityId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  protected MsgpackCodec<CompensationSubscriptionRecord> codec() {
    return CompensationSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<CompensationSubscriptionRecord> ops() {
    return CompensationSubscriptionRecordOps.INSTANCE;
  }
}
