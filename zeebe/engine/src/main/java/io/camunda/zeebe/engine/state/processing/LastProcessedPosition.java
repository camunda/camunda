/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.processing;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class LastProcessedPosition extends MsgpackBufferValue<LastProcessedPosition>
    implements DbValue {
  final RequiredLong lastProcessPosition = new RequiredLong();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void set(final long position) {
    lastProcessPosition.set(position);
  }

  public long get() {
    return lastProcessPosition.get();
  }

  @Override
  protected MsgpackCodec<LastProcessedPosition> codec() {
    return LastProcessedPositionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<LastProcessedPosition> ops() {
    return LastProcessedPositionOps.INSTANCE;
  }
}
