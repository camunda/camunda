/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

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
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class MessageCorrelationRecord extends MsgpackBufferValue<MessageCorrelationRecord>
    implements MsgpackRecordValue, MessageCorrelationRecordValue {

  @Nullable DirectBuffer name;
  @Nullable DirectBuffer correlationKey;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long messageKey = -1L;
  long requestId = -1L;
  int requestStreamId = -1;
  long processInstanceKey = -1L;
  long processDefinitionKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final MessageCorrelationRecord record) {
    setName(record.getName());
    setCorrelationKey(record.getCorrelationKey());
    setVariables(record.getVariablesBuffer());
    setTenantId(record.getTenantId());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setBusinessId(record.getBusinessIdBuffer());
  }

  @Override
  public String getName() {
    return bufferAsString(Decoding.required(name, "name"));
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(Decoding.required(correlationKey, "correlationKey"));
  }

  public MessageCorrelationRecord setCorrelationKey(final String correlationKey) {
    this.correlationKey = BufferUtil.wrapString(correlationKey);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKey;
  }

  @Override
  public long getRequestId() {
    return requestId;
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamId;
  }

  public MessageCorrelationRecord setRequestStreamId(final int requestStreamId) {
    this.requestStreamId = requestStreamId;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public MessageCorrelationRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public MessageCorrelationRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  public MessageCorrelationRecord setRequestId(final long requestId) {
    this.requestId = requestId;
    return this;
  }

  public MessageCorrelationRecord setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public MessageCorrelationRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return Decoding.required(name, "name");
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return Decoding.required(correlationKey, "correlationKey");
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public MessageCorrelationRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public MessageCorrelationRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MessageCorrelationRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public MessageCorrelationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageCorrelationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageCorrelationRecord> codec() {
    return MessageCorrelationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageCorrelationRecord> ops() {
    return MessageCorrelationRecordOps.INSTANCE;
  }
}
