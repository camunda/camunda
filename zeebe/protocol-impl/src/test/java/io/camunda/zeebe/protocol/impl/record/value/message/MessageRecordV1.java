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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class MessageRecordV1 extends UnifiedRecordValue implements MessageRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue CORRELATION_KEY_KEY = new StringValue("correlationKey");
  private static final StringValue TIME_TO_LIVE_KEY = new StringValue("timeToLive");
  private static final StringValue DEADLINE_KEY = new StringValue("deadline");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue MESSAGE_ID_KEY = new StringValue("messageId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue BUSINESS_ID_KEY = new StringValue("businessId");

  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final StringProperty correlationKeyProp = new StringProperty(CORRELATION_KEY_KEY);
  private final LongProperty timeToLiveProp = new LongProperty(TIME_TO_LIVE_KEY);
  private final LongProperty deadlineProp = new LongProperty(DEADLINE_KEY, -1);

  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final StringProperty messageIdProp = new StringProperty(MESSAGE_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty businessIdProp = new StringProperty(BUSINESS_ID_KEY, "");

  public MessageRecordV1() {
    super(8);
    declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(timeToLiveProp)
        .declareProperty(variablesProp)
        .declareProperty(messageIdProp)
        .declareProperty(deadlineProp)
        .declareProperty(tenantIdProp)
        .declareProperty(businessIdProp);
  }

  public void wrap(final MessageRecordV1 record) {
    setName(record.getNameBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setTimeToLive(record.getTimeToLive());
    setDeadline(record.getDeadline());
    setVariables(record.getVariablesBuffer());
    setMessageId(record.getMessageIdBuffer());
    setTenantId(record.getTenantId());
    setBusinessId(record.getBusinessIdBuffer());
  }

  public boolean hasMessageId() {
    return messageIdProp.getValue().capacity() > 0;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getMessageIdBuffer() {
    return messageIdProp.getValue();
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  @Override
  public String getMessageId() {
    return bufferAsString(messageIdProp.getValue());
  }

  @Override
  public long getTimeToLive() {
    return timeToLiveProp.getValue();
  }

  public MessageRecordV1 setTimeToLive(final long timeToLive) {
    timeToLiveProp.setValue(timeToLive);
    return this;
  }

  @Override
  public long getDeadline() {
    return deadlineProp.getValue();
  }

  public MessageRecordV1 setDeadline(final long deadline) {
    deadlineProp.setValue(deadline);
    return this;
  }

  public MessageRecordV1 setMessageId(final String messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecordV1 setMessageId(final DirectBuffer messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecordV1 setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecordV1 setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecordV1 setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public MessageRecordV1 setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public MessageRecordV1 setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public MessageRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessIdProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessIdProp.getValue();
  }

  public MessageRecordV1 setBusinessId(final String businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  public MessageRecordV1 setBusinessId(final DirectBuffer businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }
}
