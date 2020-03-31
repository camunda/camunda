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
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import org.agrona.DirectBuffer;

public final class MessageStartEventSubscriptionRecord extends UnifiedRecordValue
    implements MessageStartEventSubscriptionRecordValue {

  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty startEventIdProp = new StringProperty("startEventId", "");

  public MessageStartEventSubscriptionRecord() {
    declareProperty(workflowKeyProp)
        .declareProperty(messageNameProp)
        .declareProperty(startEventIdProp)
        .declareProperty(bpmnProcessIdProp);
  }

  public void wrap(final MessageStartEventSubscriptionRecord record) {
    workflowKeyProp.setValue(record.getWorkflowKey());
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
  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public MessageStartEventSubscriptionRecord setWorkflowKey(final long key) {
    workflowKeyProp.setValue(key);
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
}
