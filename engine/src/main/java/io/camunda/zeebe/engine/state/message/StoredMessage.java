/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;

public class StoredMessage extends UnpackedObject implements DbValue {

  private final LongProperty messageKeyProp = new LongProperty("messageKey");
  private final LongProperty requestIdProp = new LongProperty("requestId", -1L);
  private final IntegerProperty requestStreamIdProp = new IntegerProperty("requestStreamId", -1);

  private final ObjectProperty<MessageRecord> messageProp =
      new ObjectProperty<>("message", new MessageRecord());

  public StoredMessage() {
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

  public long getRequestId() {
    return requestIdProp.getValue();
  }

  public StoredMessage setRequestId(final long requestId) {
    requestIdProp.setValue(requestId);
    return this;
  }

  public int getRequestStreamId() {
    return requestStreamIdProp.getValue();
  }

  public StoredMessage setRequestStreamId(final int requestStreamId) {
    requestStreamIdProp.setValue(requestStreamId);
    return this;
  }
}
