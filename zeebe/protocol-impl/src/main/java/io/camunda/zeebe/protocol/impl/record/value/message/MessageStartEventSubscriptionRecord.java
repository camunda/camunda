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
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MessageStartEventSubscriptionRecord
    extends MsgpackBufferValue<MessageStartEventSubscriptionRecord>
    implements MsgpackRecordValue, MessageStartEventSubscriptionRecordValue {

  long processDefinitionKey = -1L;
  DirectBuffer messageName = Decoding.EMPTY;
  DirectBuffer startEventId = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processInstanceKey = -1L;
  long messageKey = -1L;
  DirectBuffer correlationKey = Decoding.EMPTY;
  DirectBuffer businessId = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final MessageStartEventSubscriptionRecord record) {
    processDefinitionKey = record.getProcessDefinitionKey();
    bpmnProcessId = record.getBpmnProcessIdBuffer();
    messageName = record.getMessageNameBuffer();
    startEventId = record.getStartEventIdBuffer();
    processInstanceKey = record.getProcessInstanceKey();
    messageKey = record.getMessageKey();
    correlationKey = record.getCorrelationKeyBuffer();
    businessId = record.getBusinessIdBuffer();
    variables = record.getVariablesBuffer();
    tenantId = BufferUtil.wrapString(record.getTenantId());
    storageOrdinal = record.getStorageOrdinal();
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageName;
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventId;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public MessageStartEventSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public String getStartEventId() {
    return bufferAsString(startEventId);
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageName);
  }

  public MessageStartEventSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKey);
  }

  public MessageStartEventSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
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

  public MessageStartEventSubscriptionRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKey;
  }

  public MessageStartEventSubscriptionRecord setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public MessageStartEventSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKey = key;
    return this;
  }

  public MessageStartEventSubscriptionRecord setStartEventId(final DirectBuffer startEventId) {
    this.startEventId = startEventId;
    return this;
  }

  public MessageStartEventSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public MessageStartEventSubscriptionRecord setVariables(final DirectBuffer variables) {
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

  public MessageStartEventSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageStartEventSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageStartEventSubscriptionRecord> codec() {
    return MessageStartEventSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartEventSubscriptionRecord> ops() {
    return MessageStartEventSubscriptionRecordOps.INSTANCE;
  }
}
