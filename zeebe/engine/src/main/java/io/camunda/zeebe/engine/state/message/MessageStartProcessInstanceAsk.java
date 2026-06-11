/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Value stored in the pending cross-partition message-start ask CF on {@code P_K}. Contains all
 * fields required to re-send the ask on a retry, sourced from the original {@link
 * MessageStartProcessInstanceRequestRecord}.
 *
 * <p>Keyed by {@code (messageKey, processDefinitionKey)} — the pair that uniquely identifies an
 * outstanding ask from this partition. When any of the three reply intents is applied, the entry is
 * removed.
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
public final class MessageStartProcessInstanceAsk extends UnpackedObject implements DbValue {

  private final LongProperty messageKeyProp = new LongProperty("messageKey", -1L);
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty businessIdProp = new StringProperty("businessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty startEventIdProp = new StringProperty("startEventId", "");
  private final LongProperty messageStartEventSubscriptionKeyProp =
      new LongProperty("messageStartEventSubscriptionKey", -1L);
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty messageDeadlineProp = new LongProperty("messageDeadline", -1L);
  private final LongProperty rejectionCountProp = new LongProperty("rejectionCount", 0L);

  public MessageStartProcessInstanceAsk() {
    super(12);
    declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(businessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(startEventIdProp)
        .declareProperty(messageStartEventSubscriptionKeyProp)
        .declareProperty(variablesProp)
        .declareProperty(tenantIdProp)
        .declareProperty(messageDeadlineProp)
        .declareProperty(rejectionCountProp);
  }

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
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageStartProcessInstanceAsk setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  public MessageStartProcessInstanceAsk setMessageName(final String messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public MessageStartProcessInstanceAsk setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  public MessageStartProcessInstanceAsk setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageStartProcessInstanceAsk setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public DirectBuffer getBusinessIdBuffer() {
    return businessIdProp.getValue();
  }

  public MessageStartProcessInstanceAsk setBusinessId(final String businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  public MessageStartProcessInstanceAsk setBusinessId(final DirectBuffer businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public MessageStartProcessInstanceAsk setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  public MessageStartProcessInstanceAsk setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public MessageStartProcessInstanceAsk setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public DirectBuffer getStartEventIdBuffer() {
    return startEventIdProp.getValue();
  }

  public MessageStartProcessInstanceAsk setStartEventId(final String startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  public MessageStartProcessInstanceAsk setStartEventId(final DirectBuffer startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  public long getMessageStartEventSubscriptionKey() {
    return messageStartEventSubscriptionKeyProp.getValue();
  }

  public MessageStartProcessInstanceAsk setMessageStartEventSubscriptionKey(
      final long messageStartEventSubscriptionKey) {
    messageStartEventSubscriptionKeyProp.setValue(messageStartEventSubscriptionKey);
    return this;
  }

  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  public MessageStartProcessInstanceAsk setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }

  public MessageStartProcessInstanceAsk setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public MessageStartProcessInstanceAsk setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public long getMessageDeadline() {
    return messageDeadlineProp.getValue();
  }

  public MessageStartProcessInstanceAsk setMessageDeadline(final long messageDeadline) {
    messageDeadlineProp.setValue(messageDeadline);
    return this;
  }

  /**
   * How many times this ask has been rejected. {@code 0} for an un-replied ask; incremented by the
   * rejection applier on each rejection reply. The scheduler turns it into the next-retry interval,
   * {@code min(baseInterval * 2^rejectionCount, cap)}.
   */
  public long getRejectionCount() {
    return rejectionCountProp.getValue();
  }

  public MessageStartProcessInstanceAsk setRejectionCount(final long rejectionCount) {
    rejectionCountProp.setValue(rejectionCount);
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
}
