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
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRelated;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class MessageSubscriptionRecord extends UnifiedRecordValue
    implements WorkflowInstanceRelated, MessageSubscriptionRecordValue {

  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey");
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey");
  private final LongProperty messageKeyProp = new LongProperty("messageKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);

  public MessageSubscriptionRecord() {
    this.declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(closeOnCorrelateProp);
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCorrelationKeyBuffer() {
    return correlationKeyProp.getValue();
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  @Override
  public String getMessageName() {
    return BufferUtil.bufferAsString(messageNameProp.getValue());
  }

  @Override
  public String getCorrelationKey() {
    return BufferUtil.bufferAsString(correlationKeyProp.getValue());
  }

  public MessageSubscriptionRecord setCorrelationKey(DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
    return this;
  }

  public MessageSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public MessageSubscriptionRecord setElementInstanceKey(long key) {
    elementInstanceKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public MessageSubscriptionRecord setMessageKey(long messageKey) {
    this.messageKeyProp.setValue(messageKey);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public MessageSubscriptionRecord setWorkflowInstanceKey(long key) {
    workflowInstanceKeyProp.setValue(key);
    return this;
  }

  public MessageSubscriptionRecord setCloseOnCorrelate(boolean closeOnCorrelate) {
    this.closeOnCorrelateProp.setValue(closeOnCorrelate);
    return this;
  }
}
