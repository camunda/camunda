/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class TimerInstance extends MsgpackBufferValue<TimerInstance>
    implements DbValue, TenantOwned {

  public static final long NO_ELEMENT_INSTANCE = -1L;

  DirectBuffer handlerNodeId = Decoding.EMPTY;
  long processDefinitionKey = 0L;
  long key = 0L;
  long elementInstanceKey = 0L;
  long processInstanceKey = 0L;
  long dueDate = 0L;
  int repetitions = 0;
  DirectBuffer tenantId = wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = NO_ELEMENT_INSTANCE;
  int storageOrdinal = 0;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public long getDueDate() {
    return dueDate;
  }

  public void setDueDate(final long dueDate) {
    this.dueDate = dueDate;
  }

  public long getKey() {
    return key;
  }

  public void setKey(final long key) {
    this.key = key;
  }

  public DirectBuffer getHandlerNodeId() {
    return handlerNodeId;
  }

  public void setHandlerNodeId(final DirectBuffer handlerNodeId) {
    this.handlerNodeId = handlerNodeId;
  }

  public int getRepetitions() {
    return repetitions;
  }

  public void setRepetitions(final int repetitions) {
    this.repetitions = repetitions;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public TimerInstance setTenantId(final String tenantId) {
    this.tenantId = wrapString(tenantId);
    return this;
  }

  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public TimerInstance setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public TimerInstance setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public TimerInstance setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = wrapString(bpmnProcessId);
    return this;
  }

  public BpmnElementType getElementType() {
    return elementType;
  }

  public TimerInstance setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  @Override
  protected MsgpackCodec<TimerInstance> codec() {
    return TimerInstanceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<TimerInstance> ops() {
    return TimerInstanceOps.INSTANCE;
  }
}
