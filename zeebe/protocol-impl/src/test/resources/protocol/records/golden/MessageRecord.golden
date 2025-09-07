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

public final class MessageRecord extends UnifiedRecordValue implements MessageRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue CORRELATION_KEY_KEY = new StringValue("correlationKey");
  private static final StringValue TIME_TO_LIVE_KEY = new StringValue("timeToLive");
  private static final StringValue DEADLINE_KEY = new StringValue("deadline");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue MESSAGE_ID_KEY = new StringValue("messageId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final StringProperty correlationKeyProp = new StringProperty(CORRELATION_KEY_KEY);
  private final LongProperty timeToLiveProp = new LongProperty(TIME_TO_LIVE_KEY);
  private final LongProperty deadlineProp = new LongProperty(DEADLINE_KEY, -1);

  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final StringProperty messageIdProp = new StringProperty(MESSAGE_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public MessageRecord() {
    super(7);
    declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(timeToLiveProp)
        .declareProperty(variablesProp)
        .declareProperty(messageIdProp)
        .declareProperty(deadlineProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final MessageRecord record) {
    setName(record.getNameBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setTimeToLive(record.getTimeToLive());
    setDeadline(record.getDeadline());
    setVariables(record.getVariablesBuffer());
    setMessageId(record.getMessageIdBuffer());
    setTenantId(record.getTenantId());
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

  public MessageRecord setTimeToLive(final long timeToLive) {
    timeToLiveProp.setValue(timeToLive);
    return this;
  }

  @Override
  public long getDeadline() {
    return deadlineProp.getValue();
  }

  public MessageRecord setDeadline(final long deadline) {
    deadlineProp.setValue(deadline);
    return this;
  }

  public MessageRecord setMessageId(final String messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecord setMessageId(final DirectBuffer messageId) {
    messageIdProp.setValue(messageId);
    return this;
  }

  public MessageRecord setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public MessageRecord setName(final DirectBuffer name) {
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

  public MessageRecord setVariables(final DirectBuffer variables) {
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

  public MessageRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
