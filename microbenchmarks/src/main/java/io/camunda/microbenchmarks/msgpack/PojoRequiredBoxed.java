/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.microbenchmarks.msgpack.Pojo.POJOEnum;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Like {@link PojoRequired}, but the long and the int are {@code @Nullable} boxed fields instead of
 * holders: null is unset, and reading allocates the boxes. Stores the same bytes as {@link Pojo}.
 */
@MsgpackPojo
public final class PojoRequiredBoxed extends MsgpackBufferValue<PojoRequiredBoxed> {
  @Override
  protected MsgpackCodec<PojoRequiredBoxed> codec() {
    return PojoRequiredBoxedCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoRequiredBoxed> ops() {
    return PojoRequiredBoxedOps.INSTANCE;
  }

  @Nullable POJOEnum enumProp;
  @Nullable Long longProp;
  @Nullable Integer intProp;
  @Nullable DirectBuffer stringProp;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer binaryProp;

  final PojoRequiredNested objectProp = new PojoRequiredNested();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoRequiredBoxed fill(final int seed) {
    enumProp = POJOEnum.values()[seed % POJOEnum.values().length];
    longProp = 123_456_789L + seed;
    intProp = 42 + seed;
    stringProp = BufferUtil.wrapString("benchmark test string " + seed);
    binaryProp = BufferUtil.wrapString("binary data " + seed);
    objectProp.foo = 987_654_321L + seed;
    return this;
  }
}
