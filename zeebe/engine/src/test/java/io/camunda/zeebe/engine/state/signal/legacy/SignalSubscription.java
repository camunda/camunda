/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.signal.legacy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.signal.legacy.SignalSubscriptionRecord;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class SignalSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<SignalSubscriptionRecord> recordProp =
      new ObjectProperty<>("signalSubscriptionRecord", new SignalSubscriptionRecord());
  private final LongProperty keyProp = new LongProperty("key");

  public SignalSubscription() {
    super(2);
    declareProperty(recordProp).declareProperty(keyProp);
  }

  public SignalSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final SignalSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public SignalSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }
}
