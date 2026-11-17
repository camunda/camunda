/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.suspension;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class SuspensionMarkerValue extends MsgpackBufferValue<SuspensionMarkerValue>
    implements DbValue {

  SuspensionState.@Nullable State suspensionState;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public SuspensionState.State getState() {
    return Decoding.required(suspensionState, "suspensionState");
  }

  public void setState(final SuspensionState.State state) {
    suspensionState = state;
  }

  @Override
  protected MsgpackCodec<SuspensionMarkerValue> codec() {
    return SuspensionMarkerValueCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<SuspensionMarkerValue> ops() {
    return SuspensionMarkerValueOps.INSTANCE;
  }
}
