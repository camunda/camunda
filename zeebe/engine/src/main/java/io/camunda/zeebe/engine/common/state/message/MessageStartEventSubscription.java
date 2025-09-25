/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;

public class MessageStartEventSubscription extends UnpackedObject implements DbValue {

  private final ObjectProperty<MessageStartEventSubscriptionRecord> recordProp =
      new ObjectProperty<>(
          "messageStartEventSubscriptionRecord", new MessageStartEventSubscriptionRecord());
  private final LongProperty keyProp = new LongProperty("key");

  public MessageStartEventSubscription() {
    super(2);
    declareProperty(recordProp).declareProperty(keyProp);
  }

  public MessageStartEventSubscriptionRecord getRecord() {
    return recordProp.getValue();
  }

  public void setRecord(final MessageStartEventSubscriptionRecord record) {
    recordProp.getValue().wrap(record);
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public MessageStartEventSubscription setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }
}
