/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

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
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ConditionalSubscriptionRecord
    extends MsgpackBufferValue<ConditionalSubscriptionRecord>
    implements MsgpackRecordValue, ConditionalSubscriptionRecordValue {

  // default to -1 for root level start events
  long scopeKey = -1L;
  long processInstanceKey = -1L;
  long elementInstanceKey = -1L;
  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer catchEventId = Decoding.EMPTY;
  boolean interrupting = true;
  DirectBuffer condition = Decoding.EMPTY;
  final List<DirectBuffer> variableNames = new ArrayList<>();
  final List<DirectBuffer> variableEvents = new ArrayList<>();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final ConditionalSubscriptionRecord record) {
    scopeKey = record.getScopeKey();
    processInstanceKey = record.getProcessInstanceKey();
    elementInstanceKey = record.getElementInstanceKey();
    processDefinitionKey = record.getProcessDefinitionKey();
    bpmnProcessId = BufferUtil.wrapString(record.getBpmnProcessId());
    catchEventId = BufferUtil.wrapString(record.getCatchEventId());
    interrupting = record.isInterrupting();
    condition = BufferUtil.wrapString(record.getCondition());
    setVariableNames(record.getVariableNames());
    setVariableEvents(record.getVariableEvents());
    tenantId = BufferUtil.wrapString(record.getTenantId());
    rootProcessInstanceKey = record.getRootProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    elementType = record.getElementType();
  }

  /**
   * The key of the scope in which the condition is evaluated. Scopes should be assigned for
   * different element types as follows:
   *
   * <p>Intermediate catch event → element itself
   *
   * <p>Boundary event → attached activity
   *
   * <p>Event subprocess start event → flow scope that is enclosing the event subprocess
   *
   * <p>Root level start event → nothing, just evaluate through endpoint call using process
   * definition key
   *
   * @return the scope key
   */
  @Override
  public long getScopeKey() {
    return scopeKey;
  }

  public ConditionalSubscriptionRecord setScopeKey(final long key) {
    scopeKey = key;
    return this;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public ConditionalSubscriptionRecord setElementInstanceKey(final long key) {
    elementInstanceKey = key;
    return this;
  }

  @JsonIgnore
  @Override
  public String getElementId() {
    return getCatchEventId();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ConditionalSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKey = key;
    return this;
  }

  /**
   * The process definition key used for root level start events to evaluate the condition.
   *
   * @return the process definition key
   */
  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ConditionalSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @Override
  public String getCatchEventId() {
    return bufferAsString(catchEventId);
  }

  @Override
  public String getCondition() {
    return BufferUtil.bufferAsString(condition);
  }

  public ConditionalSubscriptionRecord setCondition(final DirectBuffer condition) {
    this.condition = condition;
    return this;
  }

  @Override
  public List<String> getVariableNames() {
    final var names = new ArrayList<String>();
    variableNames.forEach(name -> names.add(BufferUtil.bufferAsString(name)));
    return names;
  }

  public ConditionalSubscriptionRecord setVariableNames(final List<String> variableNames) {
    this.variableNames.clear();
    variableNames.forEach(name -> this.variableNames.add(BufferUtil.wrapString(name)));
    return this;
  }

  @Override
  public List<String> getVariableEvents() {
    final var events = new ArrayList<String>();
    variableEvents.forEach(event -> events.add(BufferUtil.bufferAsString(event)));
    return events;
  }

  public ConditionalSubscriptionRecord setVariableEvents(final List<String> variableEvents) {
    this.variableEvents.clear();
    variableEvents.forEach(event -> this.variableEvents.add(BufferUtil.wrapString(event)));
    return this;
  }

  @Override
  public boolean isInterrupting() {
    return interrupting;
  }

  public ConditionalSubscriptionRecord setInterrupting(final boolean interrupting) {
    this.interrupting = interrupting;
    return this;
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  public ConditionalSubscriptionRecord setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  public ConditionalSubscriptionRecord setCatchEventId(final DirectBuffer catchEventId) {
    this.catchEventId = catchEventId;
    return this;
  }

  public ConditionalSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCatchEventIdBuffer() {
    return catchEventId;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ConditionalSubscriptionRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ConditionalSubscriptionRecord setRootProcessInstanceKey(final long key) {
    rootProcessInstanceKey = key;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ConditionalSubscriptionRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ConditionalSubscriptionRecord> codec() {
    return ConditionalSubscriptionRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ConditionalSubscriptionRecord> ops() {
    return ConditionalSubscriptionRecordOps.INSTANCE;
  }
}
