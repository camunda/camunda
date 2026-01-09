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
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class MessageCorrelationRecord extends UnifiedRecordValue
    implements MessageCorrelationRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue CORRELATION_KEY_KEY = new StringValue("correlationKey");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue MESSAGE_KEY_KEY = new StringValue("messageKey");
  private static final StringValue REQUEST_ID_KEY = new StringValue("requestId");
  private static final StringValue REQUEST_STREAM_ID_KEY = new StringValue("requestStreamId");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");

  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final StringProperty correlationKeyProp = new StringProperty(CORRELATION_KEY_KEY);
  private final DocumentProperty variablesProp = new DocumentProperty(VARIABLES_KEY);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  private final LongProperty messageKey = new LongProperty(MESSAGE_KEY_KEY, -1L);
  private final LongProperty requestIdProp = new LongProperty(REQUEST_ID_KEY, -1L);
  private final IntegerProperty requestStreamIdProp =
      new IntegerProperty(REQUEST_STREAM_ID_KEY, -1);
  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);

  public MessageCorrelationRecord() {
    super(7);
    declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(variablesProp)
        .declareProperty(tenantIdProp)
        .declareProperty(messageKey)
        .declareProperty(requestIdProp)
        .declareProperty(requestStreamIdProp)
        .declareProperty(processInstanceKeyProp);
  }

  public void wrap(final MessageCorrelationRecord record) {
    setName(record.getName());
    setCorrelationKey(record.getCorrelationKey());
    setVariables(record.getVariablesBuffer());
    setTenantId(record.getTenantId());
    setProcessInstanceKey(record.getProcessInstanceKey());
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public MessageCorrelationRecord setCorrelationKey(final String correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKey.getValue();
  }

  @Override
  public long getRequestId() {
    return requestIdProp.getValue();
  }

  @Override
  public int getRequestStreamId() {
    return requestStreamIdProp.getValue();
  }

  public MessageCorrelationRecord setRequestStreamId(final int requestStreamId) {
    requestStreamIdProp.setValue(requestStreamId);
    return this;
  }

  public MessageCorrelationRecord setRequestId(final long requestId) {
    requestIdProp.setValue(requestId);
    return this;
  }

  public MessageCorrelationRecord setMessageKey(final long messageKey) {
    this.messageKey.setValue(messageKey);
    return this;
  }

  public MessageCorrelationRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public MessageCorrelationRecord setVariables(final DirectBuffer variables) {
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

  public MessageCorrelationRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageCorrelationRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @JsonIgnore
  @Override
  public long getProcessDefinitionKey() {
    return -1L;
  }
}
