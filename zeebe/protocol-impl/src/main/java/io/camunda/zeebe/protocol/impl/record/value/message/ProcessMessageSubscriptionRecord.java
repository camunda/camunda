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
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@SuppressWarnings("java:S2160")
@MsgpackPojo
@NullMarked
public final class ProcessMessageSubscriptionRecord
    extends MsgpackBufferValue<ProcessMessageSubscriptionRecord>
    implements MsgpackRecordValue, ProcessMessageSubscriptionRecordValue {

  final RequiredInt subscriptionPartitionId = new RequiredInt();
  final RequiredLong processInstanceKey = new RequiredLong();
  final RequiredLong elementInstanceKey = new RequiredLong();
  long processDefinitionKey = -1L;
  long messageKey = -1L;
  DirectBuffer messageName = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  boolean interrupting = true;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer correlationKey = Decoding.EMPTY;
  DirectBuffer elementId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  long subscriptionKey = -1L;
  boolean closedForSuspend = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final ProcessMessageSubscriptionRecord record) {
    setSubscriptionPartitionId(record.getSubscriptionPartitionId());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setElementInstanceKey(record.getElementInstanceKey());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageNameBuffer());
    setVariables(record.getVariablesBuffer());
    setInterrupting(record.isInterrupting());
    setBpmnProcessId(record.getBpmnProcessIdBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setElementId(record.getElementIdBuffer());
    setTenantId(record.getTenantId());
    setRootProcessInstanceKey(record.getRootProcessInstanceKey());
    setStorageOrdinal(record.getStorageOrdinal());
    setBusinessId(record.getBusinessIdBuffer());
    setElementType(record.getElementType());
    setSubscriptionKey(record.getSubscriptionKey());
    setClosedForSuspend(record.isClosedForSuspend());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageName;
  }

  @JsonIgnore
  public int getSubscriptionPartitionId() {
    return subscriptionPartitionId.get();
  }

  public ProcessMessageSubscriptionRecord setSubscriptionPartitionId(final int partitionId) {
    subscriptionPartitionId.set(partitionId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public ProcessMessageSubscriptionRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey.get();
  }

  public ProcessMessageSubscriptionRecord setElementInstanceKey(final long key) {
    this.elementInstanceKey.set(key);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessMessageSubscriptionRecord setProcessDefinitionKey(final long key) {
    this.processDefinitionKey = key;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public ProcessMessageSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKey;
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageName);
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKey);
  }

  public ProcessMessageSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  @Override
  public boolean isInterrupting() {
    return interrupting;
  }

  public ProcessMessageSubscriptionRecord setInterrupting(final boolean interrupting) {
    this.interrupting = interrupting;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessMessageSubscriptionRecord setRootProcessInstanceKey(final long key) {
    this.rootProcessInstanceKey = key;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public ProcessMessageSubscriptionRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public ProcessMessageSubscriptionRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public ProcessMessageSubscriptionRecord setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  public ProcessMessageSubscriptionRecord setElementId(final DirectBuffer elementId) {
    this.elementId = elementId;
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public ProcessMessageSubscriptionRecord setProcessInstanceKey(final long key) {
    this.processInstanceKey.set(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessMessageSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
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

  public ProcessMessageSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public long getSubscriptionKey() {
    return subscriptionKey;
  }

  public ProcessMessageSubscriptionRecord setSubscriptionKey(final long subscriptionKey) {
    this.subscriptionKey = subscriptionKey;
    return this;
  }

  @Override
  public boolean isClosedForSuspend() {
    return closedForSuspend;
  }

  public ProcessMessageSubscriptionRecord setClosedForSuspend(final boolean closedForSuspend) {
    this.closedForSuspend = closedForSuspend;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessMessageSubscriptionRecord> codec() {
    return ProcessMessageSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessMessageSubscriptionRecord> ops() {
    return ProcessMessageSubscriptionRecordOps.INSTANCE;
  }
}
