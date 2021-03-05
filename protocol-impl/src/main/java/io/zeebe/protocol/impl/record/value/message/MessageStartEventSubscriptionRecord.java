/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.record.value.message;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class MessageStartEventSubscriptionRecord extends UnifiedRecordValue
    implements MessageStartEventSubscriptionRecordValue {

  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty startEventIdProp = new StringProperty("startEventId", "");

  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty messageKeyProp = new LongProperty("messageKey", -1L);
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");

  public MessageStartEventSubscriptionRecord() {
    declareProperty(processDefinitionKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(startEventIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(variablesProp);
  }

  public void wrap(final MessageStartEventSubscriptionRecord record) {
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    messageNameProp.setValue(record.getMessageNameBuffer());
    startEventIdProp.setValue(record.getStartEventIdBuffer());
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventIdProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public String getStartEventId() {
    return bufferAsString(startEventIdProp.getValue());
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageNameProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public MessageStartEventSubscriptionRecord setStartEventId(final DirectBuffer startEventId) {
    startEventIdProp.setValue(startEventId);
    return this;
  }

  public MessageStartEventSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }
}
