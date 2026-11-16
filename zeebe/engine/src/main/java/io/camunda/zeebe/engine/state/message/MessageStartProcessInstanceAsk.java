/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Value stored in the pending cross-partition message-start ask CF on {@code P_K}. Contains all
 * fields required to re-send the ask on a retry, sourced from the original {@link
 * MessageStartProcessInstanceRequestRecord}.
 *
 * <p>Keyed by {@code (messageKey, processDefinitionKey)} — the pair that uniquely identifies an
 * outstanding ask from this partition. The entry is removed when the ask succeeds ({@code STARTED}
 * applied locally) or the buffered message expires; a rejection ({@code UNIQUENESS_REJECTED} /
 * {@code NO_SUBSCRIPTION_REJECTED}) instead keeps the entry and increments its {@code
 * rejectionCount} so it is retried under back-off.
 *
 * <p>The {@code rejectionCount} field is {@code P_K}-local retry bookkeeping — how many times this
 * ask has been rejected so far — not part of the cross-partition request. It is deliberately absent
 * from {@link #wrap(MessageStartProcessInstanceRequestRecord)} and {@link
 * #populateRecord(MessageStartProcessInstanceRequestRecord)}: an un-replied ask carries a count of
 * {@code 0} (the base re-send cadence), and each rejection reply increments it via the rejection
 * applier — a pure, clock-free function of the prior persisted value, so it stays deterministic on
 * replay. The applier stores only the count, not a back-off duration: deriving the duration needs
 * the configured base/cap, and appliers must not read mutable config. The scheduler (which may read
 * config) turns the count into the next-retry interval, {@code min(baseInterval * 2^rejectionCount,
 * cap)}. Persisting the count — rather than holding it in the transient send tracker — keeps a
 * long-blocked ask from resetting to base-interval storming on every leader change. It defaults to
 * {@code 0} so values persisted before this field existed decode unchanged.
 */
@MsgpackPojo
@NullMarked
public final class MessageStartProcessInstanceAsk
    extends MsgpackBufferValue<MessageStartProcessInstanceAsk> implements DbValue {

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

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long messageDeadline = -1L;
  long messageTtl = 0L;
  long rejectionCount = 0L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * Copy values from a request record into this value object.
   *
   * @param record the source record
   * @return this, for chaining
   */
  public MessageStartProcessInstanceAsk wrap(
      final MessageStartProcessInstanceRequestRecord record) {
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageName());
    setCorrelationKey(record.getCorrelationKey());
    setBusinessId(record.getBusinessId());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setBpmnProcessId(record.getBpmnProcessId());
    setStartEventId(record.getStartEventId());
    setMessageStartEventSubscriptionKey(record.getMessageStartEventSubscriptionKey());
    setVariables(record.getVariablesBuffer());
    setTenantId(record.getTenantId());
    setMessageDeadline(record.getMessageDeadline());
    setMessageTtl(record.getMessageTtl());
    return this;
  }

  /**
   * Populate a request record from this value object.
   *
   * @param record the target record to populate
   */
  public void populateRecord(final MessageStartProcessInstanceRequestRecord record) {
    record.setMessageKey(getMessageKey());
    record.setMessageName(getMessageNameBuffer());
    record.setCorrelationKey(getCorrelationKeyBuffer());
    record.setBusinessId(getBusinessIdBuffer());
    record.setProcessDefinitionKey(getProcessDefinitionKey());
    record.setBpmnProcessId(getBpmnProcessIdBuffer());
    record.setStartEventId(getStartEventIdBuffer());
    record.setMessageStartEventSubscriptionKey(getMessageStartEventSubscriptionKey());
    record.setVariables(getVariablesBuffer());
    record.setTenantId(getTenantIdBuffer());
    record.setMessageDeadline(getMessageDeadline());
    record.setMessageTtl(getMessageTtl());
  }

  public long getMessageKey() {
    return messageKey;
  }

  public MessageStartProcessInstanceAsk setMessageKey(final long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public DirectBuffer getMessageNameBuffer() {
    return messageName;
  }

  public MessageStartProcessInstanceAsk setMessageName(final String messageName) {
    this.messageName = BufferUtil.wrapString(messageName);
    return this;
  }

  public MessageStartProcessInstanceAsk setMessageName(final DirectBuffer messageName) {
    this.messageName = messageName;
    return this;
  }

  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKey;
  }

  public MessageStartProcessInstanceAsk setCorrelationKey(final String correlationKey) {
    this.correlationKey = BufferUtil.wrapString(correlationKey);
    return this;
  }

  public MessageStartProcessInstanceAsk setCorrelationKey(final DirectBuffer correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  public MessageStartProcessInstanceAsk setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public MessageStartProcessInstanceAsk setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public MessageStartProcessInstanceAsk setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  public MessageStartProcessInstanceAsk setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public MessageStartProcessInstanceAsk setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public DirectBuffer getStartEventIdBuffer() {
    return startEventId;
  }

  public MessageStartProcessInstanceAsk setStartEventId(final String startEventId) {
    this.startEventId = BufferUtil.wrapString(startEventId);
    return this;
  }

  public MessageStartProcessInstanceAsk setStartEventId(final DirectBuffer startEventId) {
    this.startEventId = startEventId;
    return this;
  }

  public long getMessageStartEventSubscriptionKey() {
    return messageStartEventSubscriptionKey;
  }

  public MessageStartProcessInstanceAsk setMessageStartEventSubscriptionKey(
      final long messageStartEventSubscriptionKey) {
    this.messageStartEventSubscriptionKey = messageStartEventSubscriptionKey;
    return this;
  }

  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  public MessageStartProcessInstanceAsk setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantId;
  }

  public MessageStartProcessInstanceAsk setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public MessageStartProcessInstanceAsk setTenantId(final DirectBuffer tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public long getMessageDeadline() {
    return messageDeadline;
  }

  public MessageStartProcessInstanceAsk setMessageDeadline(final long messageDeadline) {
    this.messageDeadline = messageDeadline;
    return this;
  }

  /**
   * The TTL (millis) of the originating published message, re-emitted verbatim on every retry so
   * {@code P_B}'s expiry guard sees the same value the first ask carried. Defaults to {@code 0}
   * (fire-and-forget), matching {@code MessageRecord.timeToLive}.
   */
  public long getMessageTtl() {
    return messageTtl;
  }

  public MessageStartProcessInstanceAsk setMessageTtl(final long messageTtl) {
    this.messageTtl = messageTtl;
    return this;
  }

  /**
   * How many times this ask has been rejected. {@code 0} for an un-replied ask; incremented by the
   * rejection applier on each rejection reply. The scheduler turns it into the next-retry interval,
   * {@code min(baseInterval * 2^rejectionCount, cap)}.
   */
  public long getRejectionCount() {
    return rejectionCount;
  }

  public MessageStartProcessInstanceAsk setRejectionCount(final long rejectionCount) {
    this.rejectionCount = rejectionCount;
    return this;
  }

  /** Returns a copy of this ask that can be safely stored after the current transaction. */
  public MessageStartProcessInstanceAsk copy() {
    final var copy = new MessageStartProcessInstanceAsk();
    final var length = getLength();
    final var buffer = new UnsafeBuffer(new byte[length]);
    write(buffer, 0);
    copy.wrap(buffer, 0, length);
    return copy;
  }

  @Override
  protected MsgpackCodec<MessageStartProcessInstanceAsk> codec() {
    return MessageStartProcessInstanceAskCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartProcessInstanceAsk> ops() {
    return MessageStartProcessInstanceAskOps.INSTANCE;
  }
}
