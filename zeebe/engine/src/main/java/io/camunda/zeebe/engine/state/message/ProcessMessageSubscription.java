/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;

public final class ProcessMessageSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<ProcessMessageSubscriptionRecord> recordProp =
      new ObjectProperty<>("record", new ProcessMessageSubscriptionRecord());
  private final EnumProperty<State> stateProp =
      new EnumProperty<>("state", State.class, State.STATE_OPENING);
  private final LongProperty keyProp = new LongProperty("key");

  public ProcessMessageSubscription() {
    super(3);
    declareProperty(recordProp).declareProperty(stateProp).declareProperty(keyProp);
  }

  public ProcessMessageSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public ProcessMessageSubscription setRecord(final ProcessMessageSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
    return this;
  }

  public boolean isOpening() {
    return stateProp.getValue() == State.STATE_OPENING;
  }

  public ProcessMessageSubscription setOpening() {
    stateProp.setValue(State.STATE_OPENING);
    return this;
  }

  public ProcessMessageSubscription setOpened() {
    stateProp.setValue(State.STATE_OPENED);
    return this;
  }

  public boolean isClosing() {
    return stateProp.getValue() == State.STATE_CLOSING;
  }

  public ProcessMessageSubscription setClosing() {
    stateProp.setValue(State.STATE_CLOSING);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public ProcessMessageSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public State getState() {
    return stateProp.getValue();
  }

  public State setState(final State state) {
    stateProp.setValue(state);
    return state;
  }

  public enum State {
    STATE_OPENING,
    STATE_OPENED,
    STATE_CLOSING,
  }
}
