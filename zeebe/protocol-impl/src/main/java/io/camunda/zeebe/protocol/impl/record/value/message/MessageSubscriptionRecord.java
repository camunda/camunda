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
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MessageSubscriptionRecord extends MsgpackBufferValue<MessageSubscriptionRecord>
    implements MsgpackRecordValue, MessageSubscriptionRecordValue {

  final RequiredLong processInstanceKey = new RequiredLong();
  final RequiredLong elementInstanceKey = new RequiredLong();
  long processDefinitionKey = -1L;
  long messageKey = -1L;
  DirectBuffer messageName = Decoding.EMPTY;
  DirectBuffer correlationKey = Decoding.EMPTY;
  boolean interrupting = true;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  DirectBuffer businessId = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  long subscriptionKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final MessageSubscriptionRecord record) {
    setProcessInstanceKey(record.getProcessInstanceKey());
    setElementInstanceKey(record.getElementInstanceKey());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageNameBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setInterrupting(record.isInterrupting());
    setBpmnProcessId(record.getBpmnProcessIdBuffer());
    setVariables(record.getVariablesBuffer());
    setTenantId(record.getTenantId());
    setBusinessId(record.getBusinessIdBuffer());
    setElementId(record.getElementIdBuffer());
    setRootProcessInstanceKey(record.getRootProcessInstanceKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setElementType(record.getElementType());
    setSubscriptionKey(record.getSubscriptionKey());
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageName;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey.get();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageName);
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKey);
  }

  public MessageSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKey;
  }

  @Override
  public boolean isInterrupting() {
    return interrupting;
  }

  public MessageSubscriptionRecord setInterrupting(final boolean interrupting) {
    this.interrupting = interrupting;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public MessageSubscriptionRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public MessageSubscriptionRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  public MessageSubscriptionRecord setElementId(final DirectBuffer elementId) {
    this.elementId = elementId;
    return this;
  }

  public MessageSubscriptionRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public MessageSubscriptionRecord setRootProcessInstanceKey(final long key) {
    this.rootProcessInstanceKey = key;
    return this;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public MessageSubscriptionRecord setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  public MessageSubscriptionRecord setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public MessageSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
    return this;
  }

  public MessageSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public MessageSubscriptionRecord setProcessDefinitionKey(final long key) {
    this.processDefinitionKey = key;
    return this;
  }

  public MessageSubscriptionRecord setElementInstanceKey(final long key) {
    this.elementInstanceKey.set(key);
    return this;
  }

  public MessageSubscriptionRecord setProcessInstanceKey(final long key) {
    this.processInstanceKey.set(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public MessageSubscriptionRecord setVariables(final DirectBuffer variables) {
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

  public MessageSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public long getSubscriptionKey() {
    return subscriptionKey;
  }

  public MessageSubscriptionRecord setSubscriptionKey(final long subscriptionKey) {
    this.subscriptionKey = subscriptionKey;
    return this;
  }

  @Override
  protected MsgpackCodec<MessageSubscriptionRecord> codec() {
    return MessageSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageSubscriptionRecord> ops() {
    return MessageSubscriptionRecordOps.INSTANCE;
  }
}
