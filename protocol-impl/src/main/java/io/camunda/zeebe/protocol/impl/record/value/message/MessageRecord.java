/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class MessageRecord extends UnifiedRecordValue implements MessageRecordValue {

  private final StringProperty nameProp = new StringProperty("name");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey");
  // TTL in milliseconds
  private final LongProperty timeToLiveProp = new LongProperty("timeToLive");
  private final LongProperty deadlineProp = new LongProperty("deadline", -1);

  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final StringProperty messageIdProp = new StringProperty("messageId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public MessageRecord() {
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
    setTenantId(record.getTenantIdBuffer());
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
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKeyProp.getValue());
  }

  @Override
  public String getMessageId() {
    return BufferUtil.bufferAsString(messageIdProp.getValue());
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

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public MessageRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public MessageRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
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
}
