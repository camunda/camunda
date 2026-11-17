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
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Implementation of {@link MessageStartProcessInstanceRequestRecordValue}.
 *
 * <p>This record is exchanged across partitions to delegate message-start-event PI creation from
 * {@code P_K = hash(correlationKey)} to {@code P_B = hash(businessId)}; it never resides on a
 * single partition in isolation. The wire shape is fixed in this introductory commit so later
 * commits do not have to bump SBE versions to add fields — see the commit message for the complete
 * handshake design.
 *
 * <p>{@link #processInstanceKey} is only populated on success replies (intents {@code START} and
 * {@code STARTED}); it is {@code -1} on the request and on rejection replies. {@link #variables}
 * carries the originating message's variables on requests and is empty on replies.
 */
@MsgpackPojo
@NullMarked
public final class MessageStartProcessInstanceRequestRecord
    extends MsgpackBufferValue<MessageStartProcessInstanceRequestRecord>
    implements MsgpackRecordValue, MessageStartProcessInstanceRequestRecordValue {

  long messageKey = -1L;
  DirectBuffer messageName = Decoding.EMPTY;
  DirectBuffer correlationKey = Decoding.EMPTY;
  DirectBuffer businessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer startEventId = Decoding.EMPTY;
  long messageStartEventSubscriptionKey = -1L;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  long processInstanceKey = -1L;
  long messageDeadline = -1L;
  long messageTtl = 0L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final MessageStartProcessInstanceRequestRecord record) {
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageName());
    setCorrelationKey(record.getCorrelationKey());
    setBusinessId(record.getBusinessId());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setBpmnProcessId(record.getBpmnProcessId());
    setStartEventId(record.getStartEventId());
    setMessageStartEventSubscriptionKey(record.getMessageStartEventSubscriptionKey());
    setVariables(record.getVariablesBuffer());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setMessageDeadline(record.getMessageDeadline());
    setMessageTtl(record.getMessageTtl());
    setTenantId(record.getTenantId());
    setStorageOrdinal(record.getStorageOrdinal());
  }

  @Override
  public long getMessageKey() {
    return messageKey;
  }

  public MessageStartProcessInstanceRequestRecord setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageName);
  }

  public MessageStartProcessInstanceRequestRecord setMessageName(final String messageName) {
    this.messageName = BufferUtil.wrapString(messageName);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageName;
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKey);
  }

  public MessageStartProcessInstanceRequestRecord setCorrelationKey(final String correlationKey) {
    this.correlationKey = BufferUtil.wrapString(correlationKey);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setCorrelationKey(
      final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public MessageStartProcessInstanceRequestRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public MessageStartProcessInstanceRequestRecord setProcessDefinitionKey(
      final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public MessageStartProcessInstanceRequestRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @Override
  public String getStartEventId() {
    return bufferAsString(startEventId);
  }

  public MessageStartProcessInstanceRequestRecord setStartEventId(final String startEventId) {
    this.startEventId = BufferUtil.wrapString(startEventId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setStartEventId(final DirectBuffer startEventId) {
    this.startEventId = startEventId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventId;
  }

  @Override
  public long getMessageStartEventSubscriptionKey() {
    return messageStartEventSubscriptionKey;
  }

  public MessageStartProcessInstanceRequestRecord setMessageStartEventSubscriptionKey(
      final long messageStartEventSubscriptionKey) {
    this.messageStartEventSubscriptionKey = messageStartEventSubscriptionKey;
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public MessageStartProcessInstanceRequestRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MessageStartProcessInstanceRequestRecord setProcessInstanceKey(
      final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getMessageDeadline() {
    return messageDeadline;
  }

  public MessageStartProcessInstanceRequestRecord setMessageDeadline(final long messageDeadline) {
    this.messageDeadline = messageDeadline;
    return this;
  }

  @Override
  public long getMessageTtl() {
    return messageTtl;
  }

  public MessageStartProcessInstanceRequestRecord setMessageTtl(final long messageTtl) {
    this.messageTtl = messageTtl;
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public MessageStartProcessInstanceRequestRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecord setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageStartProcessInstanceRequestRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageStartProcessInstanceRequestRecord> codec() {
    return MessageStartProcessInstanceRequestRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartProcessInstanceRequestRecord> ops() {
    return MessageStartProcessInstanceRequestRecordOps.INSTANCE;
  }
}
