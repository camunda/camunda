/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.escalation;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.EscalationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class EscalationRecord extends MsgpackBufferValue<EscalationRecord>
    implements MsgpackRecordValue, EscalationRecordValue {

  long processInstanceKey = -1L;
  DirectBuffer escalationCode = Decoding.EMPTY;
  DirectBuffer throwElementId = Decoding.EMPTY;
  DirectBuffer catchElementId = Decoding.EMPTY;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final EscalationRecord record) {
    processInstanceKey = record.getProcessInstanceKey();
    escalationCode = BufferUtil.wrapString(record.getEscalationCode());
    throwElementId = BufferUtil.wrapString(record.getThrowElementId());
    catchElementId = BufferUtil.wrapString(record.getCatchElementId());
    storageOrdinal = record.getStorageOrdinal();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getEscalationCode() {
    return BufferUtil.bufferAsString(escalationCode);
  }

  public EscalationRecord setEscalationCode(final String escalationCode) {
    this.escalationCode = BufferUtil.wrapString(escalationCode);
    return this;
  }

  @Override
  public String getThrowElementId() {
    return BufferUtil.bufferAsString(throwElementId);
  }

  @Override
  public String getCatchElementId() {
    return BufferUtil.bufferAsString(catchElementId);
  }

  public EscalationRecord setCatchElementId(final DirectBuffer catchElementId) {
    this.catchElementId = catchElementId;
    return this;
  }

  public EscalationRecord setThrowElementId(final DirectBuffer throwElementId) {
    this.throwElementId = throwElementId;
    return this;
  }

  public EscalationRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public EscalationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public String getTenantId() {
    // todo(#13774): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }

  @Override
  protected MsgpackCodec<EscalationRecord> codec() {
    return EscalationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EscalationRecord> ops() {
    return EscalationRecordOps.INSTANCE;
  }
}
