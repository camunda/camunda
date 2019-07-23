/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.DocumentProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import io.zeebe.protocol.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public class WorkflowInstanceSubscriptionRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, WorkflowInstanceSubscriptionRecordValue {

  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty("subscriptionPartitionId");
  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty messageKeyProp = new LongProperty("messageKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);

  public WorkflowInstanceSubscriptionRecord() {
    this.declareProperty(subscriptionPartitionIdProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(variablesProp)
        .declareProperty(closeOnCorrelateProp);
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getMessageName() {
    return BufferUtil.bufferAsString(messageNameProp.getValue());
  }

  public WorkflowInstanceSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public WorkflowInstanceSubscriptionRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setMessageKey(long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public int getSubscriptionPartitionId() {
    return subscriptionPartitionIdProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setSubscriptionPartitionId(int partitionId) {
    subscriptionPartitionIdProp.setValue(partitionId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public WorkflowInstanceSubscriptionRecord setVariables(DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public WorkflowInstanceSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public WorkflowInstanceSubscriptionRecord setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelateProp.setValue(closeOnCorrelate);
    return this;
  }
}
