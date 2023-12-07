/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import org.agrona.DirectBuffer;

public final class SignalSubscriptionRecord extends UnifiedRecordValue
    implements SignalSubscriptionRecordValue {

  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty signalNameProp = new StringProperty("signalName", "");
  private final StringProperty catchEventIdProp = new StringProperty("catchEventId", "");
  private final LongProperty catchEventInstanceKeyProp =
      new LongProperty("catchEventInstanceKey", -1L);

  public SignalSubscriptionRecord() {
    super(5);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(signalNameProp)
        .declareProperty(catchEventIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(catchEventInstanceKeyProp);
  }

  public void wrap(final SignalSubscriptionRecord record) {
    processDefinitionKeyProp.setValue(record.getProcessDefinitionKey());
    bpmnProcessIdProp.setValue(record.getBpmnProcessIdBuffer());
    signalNameProp.setValue(record.getSignalNameBuffer());
    catchEventIdProp.setValue(record.getCatchEventId());
    catchEventInstanceKeyProp.setValue(record.getCatchEventInstanceKey());
  }

  @JsonIgnore
  public DirectBuffer getSignalNameBuffer() {
    return signalNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getCatchEventIdBuffer() {
    return catchEventIdProp.getValue();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessIdProp.getValue());
  }

  @Override
  public String getCatchEventId() {
    return bufferAsString(catchEventIdProp.getValue());
  }

  @Override
  public long getCatchEventInstanceKey() {
    return catchEventInstanceKeyProp.getValue();
  }

  @Override
  public String getSignalName() {
    return bufferAsString(signalNameProp.getValue());
  }

  public SignalSubscriptionRecord setSignalName(final DirectBuffer signalName) {
    signalNameProp.setValue(signalName);
    return this;
  }

  public SignalSubscriptionRecord setCatchEventInstanceKey(final long catchEventInstanceKey) {
    catchEventInstanceKeyProp.setValue(catchEventInstanceKey);
    return this;
  }

  public SignalSubscriptionRecord setCatchEventId(final DirectBuffer catchEventId) {
    catchEventIdProp.setValue(catchEventId);
    return this;
  }

  public SignalSubscriptionRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public SignalSubscriptionRecord setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessIdProp.getValue();
  }
}
