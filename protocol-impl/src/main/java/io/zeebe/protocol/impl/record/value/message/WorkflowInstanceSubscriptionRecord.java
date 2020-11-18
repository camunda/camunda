/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class WorkflowInstanceSubscriptionRecord extends UnifiedRecordValue
    implements WorkflowInstanceSubscriptionRecordValue {

  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty("subscriptionPartitionId");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty messageKeyProp = new LongProperty("messageKey", -1L);
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");

  public WorkflowInstanceSubscriptionRecord() {
    declareProperty(subscriptionPartitionIdProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(variablesProp)
        .declareProperty(closeOnCorrelateProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(correlationKeyProp);
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setElementInstanceKey(final long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  public WorkflowInstanceSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
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

  public WorkflowInstanceSubscriptionRecord setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public WorkflowInstanceSubscriptionRecord setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public WorkflowInstanceSubscriptionRecord setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
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

  public WorkflowInstanceSubscriptionRecord setSubscriptionPartitionId(final int partitionId) {
    subscriptionPartitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public WorkflowInstanceSubscriptionRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setWorkflowInstanceKey(final long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public WorkflowInstanceSubscriptionRecord setCloseOnCorrelate(final boolean closeOnCorrelate) {
    closeOnCorrelateProp.setValue(closeOnCorrelate);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }
}
