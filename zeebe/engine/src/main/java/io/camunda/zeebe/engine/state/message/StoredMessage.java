/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class StoredMessage extends MsgpackBufferValue<StoredMessage> implements DbValue {

  final RequiredLong messageKey = new RequiredLong();
  final MessageRecord message = new MessageRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getMessageKey() {
    return messageKey.get();
  }

  public StoredMessage setMessageKey(final long messageKey) {
    this.messageKey.set(messageKey);
    return this;
  }

  public MessageRecord getMessage() {
    return message;
  }

  public StoredMessage setMessage(final MessageRecord record) {
    message.wrap(record);
    return this;
  }

  // The nested record reads its values from the input buffer, so the input is copied first.
  @Override
  protected MsgpackCodec<StoredMessage> codec() {
    return StoredMessageCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<StoredMessage> ops() {
    return StoredMessageOps.INSTANCE;
  }
}
