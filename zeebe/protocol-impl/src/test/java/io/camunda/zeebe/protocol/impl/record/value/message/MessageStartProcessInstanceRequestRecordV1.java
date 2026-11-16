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
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * Implementation of {@link MessageStartProcessInstanceRequestRecordValue}.
 *
 * <p>This record is exchanged across partitions to delegate message-start-event PI creation from
 * {@code P_K = hash(correlationKey)} to {@code P_B = hash(businessId)}; it never resides on a
 * single partition in isolation. The wire shape is fixed in this introductory commit so later
 * commits do not have to bump SBE versions to add fields — see the commit message for the complete
 * handshake design.
 *
 * <p>{@link #processInstanceKeyProp} is only populated on success replies (intents {@code START}
 * and {@code STARTED}); it is {@code -1} on the request and on rejection replies. {@link
 * #variablesProp} carries the originating message's variables on requests and is empty on replies.
 */
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class MessageStartProcessInstanceRequestRecordV1 extends UnifiedRecordValue
    implements MessageStartProcessInstanceRequestRecordValue {

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
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty messageDeadlineProp = new LongProperty("messageDeadline", -1L);
  private final LongProperty messageTtlProp = new LongProperty("messageTtl", 0L);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final IntegerProperty storageOrdinalProp = new IntegerProperty("storageOrdinal", 0);

  public MessageStartProcessInstanceRequestRecordV1() {
    super(14);
    declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(businessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(startEventIdProp)
        .declareProperty(messageStartEventSubscriptionKeyProp)
        .declareProperty(variablesProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(messageDeadlineProp)
        .declareProperty(messageTtlProp)
        .declareProperty(tenantIdProp)
        .declareProperty(storageOrdinalProp);
  }

  public void wrap(final MessageStartProcessInstanceRequestRecordV1 record) {
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageName());
    setCorrelationKey(record.getCorrelationKey());
    setBusinessId(record.getBusinessId());
    setProcessDefinitionKey(record.getProcessDefinitionKey());
    setBpmnProcessId(record.getBpmnProcessId());
    setStartEventId(record.getStartEventId());
    setMessageStartEventSubscriptionKey(record.getMessageStartEventSubscriptionKey());
    setVariables(record.getVariablesBuffer());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setMessageDeadline(record.getMessageDeadline());
    setMessageTtl(record.getMessageTtl());
    setTenantId(record.getTenantId());
    setStorageOrdinal(record.getStorageOrdinal());
  }

  @Override
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageNameProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageName(final String messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setCorrelationKey(
      final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessIdProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setBusinessId(final String businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setBusinessId(final DirectBuffer businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessIdProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setProcessDefinitionKey(
      final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setBpmnProcessId(
      final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public String getStartEventId() {
    return bufferAsString(startEventIdProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setStartEventId(final String startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setStartEventId(
      final DirectBuffer startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventIdProp.getValue();
  }

  @Override
  public long getMessageStartEventSubscriptionKey() {
    return messageStartEventSubscriptionKeyProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageStartEventSubscriptionKey(
      final long messageStartEventSubscriptionKey) {
    messageStartEventSubscriptionKeyProp.setValue(messageStartEventSubscriptionKey);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setProcessInstanceKey(
      final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public long getMessageDeadline() {
    return messageDeadlineProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageDeadline(final long messageDeadline) {
    messageDeadlineProp.setValue(messageDeadline);
    return this;
  }

  @Override
  public long getMessageTtl() {
    return messageTtlProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setMessageTtl(final long messageTtl) {
    messageTtlProp.setValue(messageTtl);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public MessageStartProcessInstanceRequestRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public MessageStartProcessInstanceRequestRecordV1 setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinalProp.getValue();
  }

  public MessageStartProcessInstanceRequestRecordV1 setStorageOrdinal(final int storageOrdinal) {
    storageOrdinalProp.setValue(storageOrdinal);
    return this;
  }
}
