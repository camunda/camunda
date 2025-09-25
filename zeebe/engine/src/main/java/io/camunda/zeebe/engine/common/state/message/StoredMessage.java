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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;

public class StoredMessage extends UnpackedObject implements DbValue {

  private final LongProperty messageKeyProp = new LongProperty("messageKey");

  private final ObjectProperty<MessageRecord> messageProp =
      new ObjectProperty<>("message", new MessageRecord());

  public StoredMessage() {
    super(2);
    declareProperty(messageKeyProp).declareProperty(messageProp);
  }

  public long getMessageKey() {
    return messageKeyProp.getValue();
  }

  public StoredMessage setMessageKey(final long messageKey) {
    messageKeyProp.setValue(messageKey);
    return this;
  }

  public MessageRecord getMessage() {
    return messageProp.getValue();
  }

  public StoredMessage setMessage(final MessageRecord record) {
    messageProp.getValue().wrap(record);
    return this;
  }
}
