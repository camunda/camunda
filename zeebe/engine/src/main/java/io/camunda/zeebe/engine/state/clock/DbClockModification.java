/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
final class DbClockModification extends MsgpackBufferValue<DbClockModification> implements DbValue {
  ModificationType type = ModificationType.NONE;
  // depending on the intent, the value may be a timestamp (epoch milliseconds) or an offset (in
  // milliseconds)
  long time = 0;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  protected MsgpackCodec<DbClockModification> codec() {
    return DbClockModificationCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<DbClockModification> ops() {
    return DbClockModificationOps.INSTANCE;
  }

  DbClockModification pinAt(final long pinnedEpoch) {
    reset();
    type = ModificationType.PIN;
    time = pinnedEpoch;
    return this;
  }

  DbClockModification offsetBy(final long offsetMillis) {
    reset();
    type = ModificationType.OFFSET;
    time = offsetMillis;
    return this;
  }

  Modification modification() {
    return switch (type) {
      case NONE -> Modification.none();
      case PIN -> Modification.pinAt(Instant.ofEpochMilli(time));
      case OFFSET -> Modification.offsetBy(Duration.ofMillis(time));
    };
  }

  // Do not remove values as this could break deserialization in the future
  enum ModificationType {
    NONE,
    PIN,
    OFFSET
  };
}
