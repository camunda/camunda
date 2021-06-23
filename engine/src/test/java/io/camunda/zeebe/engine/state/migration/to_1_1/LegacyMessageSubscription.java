/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_1_1;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;

public final class LegacyMessageSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<MessageSubscriptionRecord> recordProp =
      new ObjectProperty<>("record", new MessageSubscriptionRecord());

  private final LongProperty keyProp = new LongProperty("key");

  private final LongProperty commandSentTimeProp = new LongProperty("commandSentTime", 0);

  public LegacyMessageSubscription() {
    declareProperty(recordProp).declareProperty(keyProp).declareProperty(commandSentTimeProp);
  }

  public MessageSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public LegacyMessageSubscription setRecord(final MessageSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public LegacyMessageSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
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
}
