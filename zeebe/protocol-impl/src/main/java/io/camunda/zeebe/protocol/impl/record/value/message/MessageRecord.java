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
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class MessageRecord extends MsgpackBufferValue<MessageRecord>
    implements MsgpackRecordValue, MessageRecordValue {

  @Nullable DirectBuffer name;
  @Nullable DirectBuffer correlationKey;
  final RequiredLong timeToLive = new RequiredLong();

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer messageId = Decoding.EMPTY;
  long deadline = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  DirectBuffer businessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final MessageRecord record) {
    setName(record.getNameBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setTimeToLive(record.getTimeToLive());
    setDeadline(record.getDeadline());
    setVariables(record.getVariablesBuffer());
    setMessageId(record.getMessageIdBuffer());
    setTenantId(record.getTenantId());
    setBusinessId(record.getBusinessIdBuffer());
  }

  public boolean hasMessageId() {
    return messageId.capacity() > 0;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return Decoding.required(correlationKey, "correlationKey");
  }

  @JsonIgnore
  public DirectBuffer getMessageIdBuffer() {
    return messageId;
  }

  @Override
  public String getName() {
    return bufferAsString(Decoding.required(name, "name"));
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(Decoding.required(correlationKey, "correlationKey"));
  }

  @Override
  public String getMessageId() {
    return bufferAsString(messageId);
  }

  @Override
  public long getTimeToLive() {
    return timeToLive.get();
  }

  public MessageRecord setTimeToLive(final long timeToLive) {
    this.timeToLive.set(timeToLive);
    return this;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  public MessageRecord setDeadline(final long deadline) {
    this.deadline = deadline;
    return this;
  }

  public MessageRecord setMessageId(final String messageId) {
    this.messageId = BufferUtil.wrapString(messageId);
    return this;
  }

  public MessageRecord setMessageId(final DirectBuffer messageId) {
    this.messageId = messageId;
    return this;
  }

  public MessageRecord setCorrelationKey(final String correlationKey) {
    this.correlationKey = BufferUtil.wrapString(correlationKey);
    return this;
  }

  public MessageRecord setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public MessageRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
    return this;
  }

  public MessageRecord setName(final DirectBuffer name) {
    this.name = name;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return Decoding.required(name, "name");
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public MessageRecord setVariables(final DirectBuffer variables) {
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

  public MessageRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  public MessageRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public MessageRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageRecord> codec() {
    return MessageRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageRecord> ops() {
    return MessageRecordOps.INSTANCE;
  }
}
