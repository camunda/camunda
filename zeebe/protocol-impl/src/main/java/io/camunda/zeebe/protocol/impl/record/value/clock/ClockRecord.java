/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clock;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ClockRecord extends MsgpackBufferValue<ClockRecord>
    implements MsgpackRecordValue, ClockRecordValue {

  // depending on the intent, the value may be a:
  // - timestamp (epoch milliseconds)
  // - offset (in milliseconds), either the duration to add (ADD) or the total offset (OFFSET)
  // - current time of the clock (epoch milliseconds), for FETCHED
  long time = 0L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getTime() {
    return time;
  }

  public ClockRecord pinAt(final long pinnedEpoch) {
    reset();
    time = pinnedEpoch;
    return this;
  }

  public ClockRecord offsetBy(final long offsetMillis) {
    reset();
    time = offsetMillis;
    return this;
  }

  public ClockRecord setTime(final long epochMillis) {
    time = epochMillis;
    return this;
  }

  @Override
  protected MsgpackCodec<ClockRecord> codec() {
    return ClockRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ClockRecord> ops() {
    return ClockRecordOps.INSTANCE;
  }
}
