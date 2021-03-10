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
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class ProcessInstanceSubscription extends UnpackedObject implements DbValue {

  private final StringProperty messageNameProp = new StringProperty("messageName", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty targetElementIdProp = new StringProperty("targetElementId", "");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final LongProperty elementInstanceKeyProp = new LongProperty("elementInstanceKey", -1L);
  private final IntegerProperty subscriptionPartitionIdProp =
      new IntegerProperty("subscriptionPartitionId", -1);
  private final LongProperty commandSentTimeProp = new LongProperty("commandSentTime", 0);
  private final BooleanProperty closeOnCorrelateProp =
      new BooleanProperty("closeOnCorrelate", true);
  private final EnumProperty<State> stateProp =
      new EnumProperty<>("state", State.class, State.STATE_OPENING);

  public ProcessInstanceSubscription() {
    declareProperty(messageNameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(targetElementIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(elementInstanceKeyProp)
        .declareProperty(subscriptionPartitionIdProp)
        .declareProperty(commandSentTimeProp)
        .declareProperty(closeOnCorrelateProp)
        .declareProperty(stateProp);
  }

  public ProcessInstanceSubscription(
      final long processInstanceKey,
      final long elementInstanceKey,
      final DirectBuffer targetElementId,
      final DirectBuffer bpmnProcessId,
      final DirectBuffer messageName,
      final DirectBuffer correlationKey,
      final long commandSentTime,
      final boolean closeOnCorrelate) {
    this();

    processInstanceKeyProp.setValue(processInstanceKey);
    elementInstanceKeyProp.setValue(elementInstanceKey);
    bpmnProcessIdProp.setValue(bpmnProcessId);
    targetElementIdProp.setValue(targetElementId);
    commandSentTimeProp.setValue(commandSentTime);
    messageNameProp.setValue(messageName);
    correlationKeyProp.setValue(correlationKey);
    closeOnCorrelateProp.setValue(closeOnCorrelate);
  }

  public DirectBuffer getMessageName() {
    return messageNameProp.getValue();
  }

  public void setMessageName(final DirectBuffer messageName) {
    messageNameProp.setValue(messageName);
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public void setCorrelationKey(final DirectBuffer correlationKey) {
    correlationKeyProp.setValue(correlationKey);
  }

  public DirectBuffer getTargetElementId() {
    return targetElementIdProp.getValue();
  }

  public void setTargetElementId(final DirectBuffer targetElementId) {
    targetElementIdProp.setValue(targetElementId);
  }

  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
  }

  public long getElementInstanceKey() {
    return elementInstanceKeyProp.getValue();
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    elementInstanceKeyProp.setValue(elementInstanceKey);
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public void setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
  }

  public long getCommandSentTime() {
    return commandSentTimeProp.getValue();
  }

  public void setCommandSentTime(final long commandSentTime) {
    commandSentTimeProp.setValue(commandSentTime);
  }

  public int getSubscriptionPartitionId() {
    return subscriptionPartitionIdProp.getValue();
  }

  public void setSubscriptionPartitionId(final int subscriptionPartitionId) {
    subscriptionPartitionIdProp.setValue(subscriptionPartitionId);
  }

  public boolean shouldCloseOnCorrelate() {
    return closeOnCorrelateProp.getValue();
  }

  public void setCloseOnCorrelate(final boolean closeOnCorrelate) {
    closeOnCorrelateProp.setValue(closeOnCorrelate);
  }

  public boolean isOpening() {
    return stateProp.getValue() == State.STATE_OPENING;
  }

  public boolean isClosing() {
    return stateProp.getValue() == State.STATE_CLOSING;
  }

  public void setOpened() {
    stateProp.setValue(State.STATE_OPENED);
  }

  public void setClosing() {
    stateProp.setValue(State.STATE_CLOSING);
  }

  private enum State {
    STATE_OPENING,
    STATE_OPENED,
    STATE_CLOSING,
  }
}
