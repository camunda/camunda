/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.conditional;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ConditionalSubscription extends MsgpackBufferValue<ConditionalSubscription>
    implements DbValue {

  final ConditionalSubscriptionRecord conditionalSubscriptionRecord =
      new ConditionalSubscriptionRecord();
  final RequiredLong key = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ConditionalSubscriptionRecord getRecord() {
    return conditionalSubscriptionRecord;
  }

  public void setRecord(final ConditionalSubscriptionRecord record) {
    conditionalSubscriptionRecord.wrap(record);
  }

  public long getKey() {
    return key.get();
  }

  public ConditionalSubscription setKey(final long key) {
    this.key.set(key);
    return this;
  }

  @Override
  protected MsgpackCodec<ConditionalSubscription> codec() {
    return ConditionalSubscriptionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ConditionalSubscription> ops() {
    return ConditionalSubscriptionOps.INSTANCE;
  }
}
