/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.pojo.annotation.*;
import org.agrona.concurrent.UnsafeBuffer;

@MsgpackPojo
public final class GeneratedChild extends MsgpackBufferValue<GeneratedChild> {
  long id = -1L;
  final UnsafeBuffer label = new UnsafeBuffer(Decoding.EMPTY);
  final UnknownProperties unknown = new UnknownProperties();

  @Override
  protected MsgpackCodec<GeneratedChild> codec() {
    return GeneratedChildCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<GeneratedChild> ops() {
    return GeneratedChildOps.INSTANCE;
  }

  public void fill(final int seed) {
    id = seed;
    label.wrap(("label-" + seed).getBytes());
  }
}
