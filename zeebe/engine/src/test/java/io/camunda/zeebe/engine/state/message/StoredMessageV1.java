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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecordV1;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class StoredMessageV1 extends UnpackedObject implements DbValue {

  private final LongProperty messageKeyProp = new LongProperty("messageKey");

  private final ObjectProperty<MessageRecordV1> messageProp =
      new ObjectProperty<>("message", new MessageRecordV1());

  public StoredMessageV1() {
    super(2);
    declareProperty(messageKeyProp).declareProperty(messageProp);
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public StoredMessageV1 setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public MessageRecordV1 getMessage() {
    return messageProp.getValue();
  }

  public StoredMessageV1 setMessage(final MessageRecordV1 record) {
    messageProp.getValue().wrap(record);
    return this;
  }
}
