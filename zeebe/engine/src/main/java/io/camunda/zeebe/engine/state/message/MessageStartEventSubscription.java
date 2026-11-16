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
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MessageStartEventSubscription
    extends MsgpackBufferValue<MessageStartEventSubscription> implements DbValue {

  @MsgpackProperty("messageStartEventSubscriptionRecord")
  final MessageStartEventSubscriptionRecord record = new MessageStartEventSubscriptionRecord();

  final RequiredLong key = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public MessageStartEventSubscriptionRecord getRecord() {
    return record;
  }

  public void setRecord(final MessageStartEventSubscriptionRecord record) {
    this.record.wrap(record);
  }

  public long getKey() {
    return key.get();
  }

  public MessageStartEventSubscription setKey(final long key) {
    this.key.set(key);
    return this;
  }

  // The nested record reads its values from the input buffer, so the input is copied first.
  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  @Override
  protected MsgpackCodec<MessageStartEventSubscription> codec() {
    return MessageStartEventSubscriptionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MessageStartEventSubscription> ops() {
    return MessageStartEventSubscriptionOps.INSTANCE;
  }
}
