/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BooleanProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MessageSubscription extends UnpackedObject implements DbValue {

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty messageNameProp = new StringProperty("messageName");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey");
  private final StringProperty messageVariablesProp = new StringProperty("messageVariables", "");

  private final LongProperty workflowInstanceKeyProp = new LongProperty("workflowInstanceKey", 0);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", 0);
  private final LongProperty messageKeyProp = new LongProperty("messageKey", 0);
  private final LongProperty commandSentTimeProp = new LongProperty("commandSentTime", 0);
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", false);

  public MessageSubscription() {
    declareProperty(bpmnProcessIdProp)
        .declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(messageVariablesProp)
        .declareProperty(workflowInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(messageKeyProp)
        .declareProperty(commandSentTimeProp)
        .declareProperty(closeOnCorrelateProp);
  }

  public MessageSubscription(
      final long workflowInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final boolean closeOnCorrelate) {
    this();
    workflowInstanceKeyProp.setValue(workflowInstanceKey);
    elementInstanceKeyProp.setValue(elementInstanceKey);

    bpmnProcessIdProp.setValue(bpmnProcessId);
    messageNameProp.setValue(messageName);
    correlationKeyProp.setValue(correlationKey);
    closeOnCorrelateProp.setValue(closeOnCorrelate);
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public DirectBuffer getMessageVariables() {
    return messageVariablesProp.getValue();
  }

  public void setMessageVariables(final DirectBuffer variables) {
    messageVariablesProp.setValue(variables);
  }

  public long getWorkflowInstanceKey() {
    return workflowInstanceKeyProp.getValue();
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public void setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
  }

  public long getCommandSentTime() {
    return commandSentTimeProp.getValue();
  }

  public void setCommandSentTime(final long commandSentTime) {
    commandSentTimeProp.setValue(commandSentTime);
  }

  public boolean isCorrelating() {
    return commandSentTimeProp.getValue() > 0;
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  public void setCloseOnCorrelate(final boolean closeOnCorrelate) {
    closeOnCorrelateProp.setValue(closeOnCorrelate);
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    final byte[] bytes = new byte[length];
    final MutableDirectBuffer newBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(0, bytes, 0, length);
    super.wrap(newBuffer, 0, length);
  }
}
