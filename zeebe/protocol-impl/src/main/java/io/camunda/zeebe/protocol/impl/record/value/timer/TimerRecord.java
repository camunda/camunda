/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.timer;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class TimerRecord extends MsgpackBufferValue<TimerRecord>
    implements MsgpackRecordValue, TimerRecordValue {

  final RequiredLong elementInstanceKey = new RequiredLong();
  final RequiredLong processInstanceKey = new RequiredLong();
  final RequiredLong dueDate = new RequiredLong();
  @Nullable DirectBuffer targetElementId;
  final RequiredInt repetitions = new RequiredInt();
  final RequiredLong processDefinitionKey = new RequiredLong();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return Decoding.required(targetElementId, "targetElementId");
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey.get();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey.get();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  public TimerRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey.set(processInstanceKey);
    return this;
  }

  @Override
  public long getDueDate() {
    return dueDate.get();
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(Decoding.required(targetElementId, "targetElementId"));
  }

  @Override
  public int getRepetitions() {
    return repetitions.get();
  }

  public TimerRecord setRepetitions(final int repetitions) {
    this.repetitions.set(repetitions);
    return this;
  }

  public TimerRecord setTargetElementId(final DirectBuffer targetElementId) {
    this.targetElementId = targetElementId;
    return this;
  }

  public TimerRecord setDueDate(final long dueDate) {
    this.dueDate.set(dueDate);
    return this;
  }

  public TimerRecord setElementInstanceKey(final long key) {
    elementInstanceKey.set(key);
    return this;
  }

  public TimerRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey.set(processDefinitionKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public TimerRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public TimerRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public TimerRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public TimerRecord setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public TimerRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<TimerRecord> codec() {
    return TimerRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<TimerRecord> ops() {
    return TimerRecordOps.INSTANCE;
  }
}
