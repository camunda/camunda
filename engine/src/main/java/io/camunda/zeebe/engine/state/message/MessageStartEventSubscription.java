/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.ObjectProperty;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;

public class MessageStartEventSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<MessageStartEventSubscriptionRecord> recordProp =
      new ObjectProperty<>(
          "messageStartEventSubscriptionRecord", new MessageStartEventSubscriptionRecord());
  private final LongProperty keyProp = new LongProperty("key");

  public MessageStartEventSubscription() {
    declareProperty(recordProp).declareProperty(keyProp);
  }

  public void setRecord(final MessageStartEventSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
  }

  public MessageStartEventSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public MessageStartEventSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }
}
