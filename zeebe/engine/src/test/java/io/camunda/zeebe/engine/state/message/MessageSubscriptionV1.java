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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class MessageSubscriptionV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<MessageSubscriptionRecordV1> recordProp =
      new ObjectProperty<>("record", new MessageSubscriptionRecordV1());

  private final LongProperty keyProp = new LongProperty("key");

  private final BooleanProperty correlatingProp = new BooleanProperty("correlating", false);

  public MessageSubscriptionV1() {
    super(3);
    declareProperty(recordProp).declareProperty(keyProp).declareProperty(correlatingProp);
  }

  public MessageSubscriptionRecordV1 getRecord() {
    return recordProp.getValue();
  }

  public MessageSubscriptionV1 setRecord(final MessageSubscriptionRecordV1 record) {
    recordProp.getValue().wrap(record);
    return this;
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public MessageSubscriptionV1 setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public boolean isCorrelating() {
    return correlatingProp.getValue();
  }

  public MessageSubscriptionV1 setCorrelating(final boolean correlating) {
    correlatingProp.setValue(correlating);
    return this;
  }
}
