/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecordV1;

public final class LegacyProcessMessageSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<ProcessMessageSubscriptionRecordV1> recordProp =
      new ObjectProperty<>("record", new ProcessMessageSubscriptionRecordV1());
  private final LongProperty commandSentTimeProp = new LongProperty("commandSentTime", 0);
  private final EnumProperty<State> stateProp =
      new EnumProperty<>("state", State.class, State.STATE_OPENING);
  private final LongProperty keyProp = new LongProperty("key");

  public LegacyProcessMessageSubscription() {
    super(4);
    declareProperty(recordProp)
        .declareProperty(commandSentTimeProp)
        .declareProperty(stateProp)
        .declareProperty(keyProp);
  }

  public ProcessMessageSubscriptionRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public LegacyProcessMessageSubscription setRecord(
      final ProcessMessageSubscriptionRecordV1 record) {
    recordProp.getValue().wrap(record);
    return this;
  }

  public long getCommandSentTime() {
    return commandSentTimeProp.getValue();
  }

  public LegacyProcessMessageSubscription setCommandSentTime(final long commandSentTime) {
    commandSentTimeProp.setValue(commandSentTime);
    return this;
  }

  public boolean isOpening() {
    return stateProp.getValue() == State.STATE_OPENING;
  }

  public LegacyProcessMessageSubscription setOpened() {
    stateProp.setValue(State.STATE_OPENED);
    return this;
  }

  public boolean isClosing() {
    return stateProp.getValue() == State.STATE_CLOSING;
  }

  public LegacyProcessMessageSubscription setClosing() {
    stateProp.setValue(State.STATE_CLOSING);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public LegacyProcessMessageSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  private enum State {
    STATE_OPENING,
    STATE_OPENED,
    STATE_CLOSING,
  }
}
