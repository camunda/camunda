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
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;

@SuppressWarnings("java:S2160")
public final class ProcessMessageSubscriptionRecord extends UnifiedRecordValue
    implements ProcessMessageSubscriptionRecordValue {

  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty("subscriptionPartitionId");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty messageKeyProp = new LongProperty("messageKey", -1L);
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final BooleanProperty interruptingProp = new BooleanProperty("interrupting", true);
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");

  public ProcessMessageSubscriptionRecord() {
    declareProperty(subscriptionPartitionIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(variablesProp)
        .declareProperty(interruptingProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(elementIdProp);
  }

  public void wrap(final ProcessMessageSubscriptionRecord record) {
    setSubscriptionPartitionId(record.getSubscriptionPartitionId());
    setProcessInstanceKey(record.getProcessInstanceKey());
    setElementInstanceKey(record.getElementInstanceKey());
    setMessageKey(record.getMessageKey());
    setMessageName(record.getMessageNameBuffer());
    setVariables(record.getVariablesBuffer());
    setInterrupting(record.isInterrupting());
    setBpmnProcessId(record.getBpmnProcessIdBuffer());
    setCorrelationKey(record.getCorrelationKeyBuffer());
    setElementId(record.getElementIdBuffer());
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public int getSubscriptionPartitionId() {
    return subscriptionPartitionIdProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setSubscriptionPartitionId(final int partitionId) {
    subscriptionPartitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setVariables(final DirectBuffer variables) {
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

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  @Override
  public String getMessageName() {
    return bufferAsString(messageNameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return bufferAsString(correlationKeyProp.getValue());
  }

  public ProcessMessageSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  @Override
  public String getElementId() {
    return bufferAsString(getElementIdBuffer());
  }

  @Override
  public boolean isInterrupting() {
    return interruptingProp.getValue();
  }

  public ProcessMessageSubscriptionRecord setInterrupting(final boolean interrupting) {
    interruptingProp.setValue(interrupting);
    return this;
  }

  public ProcessMessageSubscriptionRecord setElementId(final DirectBuffer elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public ProcessMessageSubscriptionRecord setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public ProcessMessageSubscriptionRecord setProcessInstanceKey(final long key) {
    processInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementIdProp.getValue();
  }
}
