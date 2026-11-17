/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue.MessageStartLockReleaseHolderValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * A single holder entry of a {@link MessageStartCorrelationKeyLockReleaseRecord}: one
 * correlation-key lock on {@code P_K} whose holder instance lives on {@code P_B}. See {@link
 * MessageStartLockReleaseHolderValue} for the field semantics.
 */
@JsonIgnoreProperties({"encodedLength", "empty", "length"})
@MsgpackPojo
@NullMarked
public final class MessageStartLockReleaseHolder
    extends MsgpackBufferValue<MessageStartLockReleaseHolder>
    implements MessageStartLockReleaseHolderValue {

  long processInstanceKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer correlationKey = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  int storageOrdinal = StorageOrdinalRelated.MAIN_INDEX;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public MessageStartLockReleaseHolder setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public MessageStartLockReleaseHolder setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public MessageStartLockReleaseHolder setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKey);
  }

  public MessageStartLockReleaseHolder setCorrelationKey(final String correlationKey) {
    this.correlationKey = BufferUtil.wrapString(correlationKey);
    return this;
  }

  public MessageStartLockReleaseHolder setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public MessageStartLockReleaseHolder setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public MessageStartLockReleaseHolder setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public MessageStartLockReleaseHolder setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public void copy(final MessageStartLockReleaseHolder other) {
    setProcessInstanceKey(other.getProcessInstanceKey());
    setBpmnProcessId(other.getBpmnProcessIdBuffer());
    setCorrelationKey(other.getCorrelationKeyBuffer());
    setTenantId(other.getTenantId());
    setStorageOrdinal(other.getStorageOrdinal());
  }

  @Override
  protected MsgpackCodec<MessageStartLockReleaseHolder> codec() {
    return MessageStartLockReleaseHolderCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartLockReleaseHolder> ops() {
    return MessageStartLockReleaseHolderOps.INSTANCE;
  }
}
