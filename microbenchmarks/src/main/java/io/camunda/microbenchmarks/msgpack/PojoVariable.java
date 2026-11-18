/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.pojo.annotation.*;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Plain data with a generated codec, stores the same bytes as {@link UnpackedVariable}. Like its
 * property, {@code key} has no default, so reading checks that it is set. Unlike its property,
 * {@code value} has a default: without one it would be a {@code @Nullable DirectBuffer}, which gets
 * a new view per read.
 */
@MsgpackPojo
public final class PojoVariable extends MsgpackBufferValue<PojoVariable> {
  final RequiredLong key = new RequiredLong();

  @Binary final UnsafeBuffer value = new UnsafeBuffer(Decoding.EMPTY);

  final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoVariable setKey(final long key) {
    this.key.set(key);
    return this;
  }

  public PojoVariable setValue(final DirectBuffer value) {
    this.value.wrap(BufferUtil.cloneBuffer(value));
    return this;
  }

  @Override
  protected MsgpackCodec<PojoVariable> codec() {
    return PojoVariableCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoVariable> ops() {
    return PojoVariableOps.INSTANCE;
  }
}
