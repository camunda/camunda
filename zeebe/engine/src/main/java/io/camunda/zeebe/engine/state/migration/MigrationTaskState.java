/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class MigrationTaskState extends MsgpackBufferValue<MigrationTaskState>
    implements DbValue {

  State state = State.NOT_STARTED;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  protected MsgpackCodec<MigrationTaskState> codec() {
    return MigrationTaskStateCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<MigrationTaskState> ops() {
    return MigrationTaskStateOps.INSTANCE;
  }

  public State getState() {
    return state;
  }

  public MigrationTaskState setState(final State state) {
    this.state = state;
    return this;
  }

  public enum State {
    NOT_STARTED(0),
    FINISHED(1);

    byte value;

    State(final int value) {
      this.value = (byte) value;
    }
  }
}
