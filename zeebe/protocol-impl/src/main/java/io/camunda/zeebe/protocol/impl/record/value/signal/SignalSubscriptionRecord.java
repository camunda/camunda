/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class SignalSubscriptionRecord extends MsgpackBufferValue<SignalSubscriptionRecord>
    implements MsgpackRecordValue, SignalSubscriptionRecordValue {

  long processDefinitionKey = -1L;
  DirectBuffer signalName = Decoding.EMPTY;
  DirectBuffer catchEventId = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long catchEventInstanceKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long processInstanceKey = -1L;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  BpmnElementType bpmnElementType = BpmnElementType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final SignalSubscriptionRecord record) {
    processDefinitionKey = record.getProcessDefinitionKey();
    bpmnProcessId = record.getBpmnProcessIdBuffer();
    signalName = record.getSignalNameBuffer();
    catchEventId = BufferUtil.wrapString(record.getCatchEventId());
    catchEventInstanceKey = record.getCatchEventInstanceKey();
    tenantId = BufferUtil.wrapString(record.getTenantId());
    processInstanceKey = record.getProcessInstanceKey();
    rootProcessInstanceKey = record.getRootProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    bpmnElementType = record.getBpmnElementType();
  }

  @JsonIgnore
  public DirectBuffer getSignalNameBuffer() {
    return signalName;
  }

  @JsonIgnore
  public DirectBuffer getCatchEventIdBuffer() {
    return catchEventId;
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
  public String getCatchEventId() {
    return bufferAsString(catchEventId);
  }

  @Override
  public long getCatchEventInstanceKey() {
    return catchEventInstanceKey;
  }

  @Override
  public String getSignalName() {
    return bufferAsString(signalName);
  }

  public SignalSubscriptionRecord setSignalName(final DirectBuffer signalName) {
    this.signalName = signalName;
    return this;
  }

  public SignalSubscriptionRecord setCatchEventInstanceKey(final long catchEventInstanceKey) {
    this.catchEventInstanceKey = catchEventInstanceKey;
    return this;
  }

  public SignalSubscriptionRecord setCatchEventId(final DirectBuffer catchEventId) {
    this.catchEventId = catchEventId;
    return this;
  }

  public SignalSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public SignalSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public long getSubscriptionKey() {
    return catchEventInstanceKey > -1 ? catchEventInstanceKey : processDefinitionKey;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public SignalSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @JsonIgnore
  @Override
  public String getElementId() {
    return getCatchEventId();
  }

  @JsonIgnore
  @Override
  public long getElementInstanceKey() {
    return getCatchEventInstanceKey();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SignalSubscriptionRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public SignalSubscriptionRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public SignalSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public BpmnElementType getBpmnElementType() {
    return bpmnElementType;
  }

  public SignalSubscriptionRecord setBpmnElementType(final BpmnElementType bpmnElementType) {
    this.bpmnElementType = bpmnElementType;
    return this;
  }

  @Override
  protected MsgpackCodec<SignalSubscriptionRecord> codec() {
    return SignalSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<SignalSubscriptionRecord> ops() {
    return SignalSubscriptionRecordOps.INSTANCE;
  }
}
