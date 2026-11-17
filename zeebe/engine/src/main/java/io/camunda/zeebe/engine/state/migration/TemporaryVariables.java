/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class TemporaryVariables extends MsgpackBufferValue<TemporaryVariables>
    implements DbValue {

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer temporaryVariables;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public DirectBuffer get() {
    return Decoding.required(temporaryVariables, "temporaryVariables");
  }

  public void set(final DirectBuffer value) {
    temporaryVariables = value;
  }

  @Override
  protected MsgpackCodec<TemporaryVariables> codec() {
    return TemporaryVariablesCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<TemporaryVariables> ops() {
    return TemporaryVariablesOps.INSTANCE;
  }
}
