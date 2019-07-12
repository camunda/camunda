/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class MessageStartEventSubscriptionRecord extends UnifiedRecordValue
    implements MessageStartEventSubscriptionRecordValue {

  private final LongProperty workflowKeyProp = new LongProperty("workflowKey");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty startEventIdProp = new StringProperty("startEventId", "");

  public MessageStartEventSubscriptionRecord() {
    this.declareProperty(workflowKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(startEventIdProp);
  }

  @JsonIgnore
  public DirectBuffer getMessageNameBuffer() {
    return messageNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getStartEventIdBuffer() {
    return startEventIdProp.getValue();
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  @Override
  public String getStartEventId() {
    return BufferUtil.bufferAsString(startEventIdProp.getValue());
  }

  @Override
  public String getMessageName() {
    return BufferUtil.bufferAsString(messageNameProp.getValue());
  }

  public MessageStartEventSubscriptionRecord setMessageName(DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
    return this;
  }

  public MessageStartEventSubscriptionRecord setStartEventId(DirectBuffer startEventId) {
    this.startEventIdProp.setValue(startEventId);
    return this;
  }

  public MessageStartEventSubscriptionRecord setWorkflowKey(long key) {
    workflowKeyProp.setValue(key);
    return this;
  }
}
