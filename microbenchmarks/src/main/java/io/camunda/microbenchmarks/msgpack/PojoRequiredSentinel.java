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
import io.camunda.zeebe.msgpack.pojo.MsgpackRequired;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Like {@link PojoRequired}, but the long and the int are {@code @MsgpackRequired} primitives
 * instead of holders, with the sentinels Long.MIN_VALUE and -1 (the {@code big} values use 0).
 * Stores the same bytes as {@link Pojo}.
 */
@MsgpackPojo
public final class PojoRequiredSentinel extends MsgpackBufferValue<PojoRequiredSentinel> {
  @Override
  protected MsgpackCodec<PojoRequiredSentinel> codec() {
    return PojoRequiredSentinelCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoRequiredSentinel> ops() {
    return PojoRequiredSentinelOps.INSTANCE;
  }

  @Nullable POJOEnum enumProp;
  @MsgpackRequired long longProp = Long.MIN_VALUE;
  @MsgpackRequired int intProp = -1;
  @Nullable DirectBuffer stringProp;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer binaryProp;

  final PojoRequiredNested objectProp = new PojoRequiredNested();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoRequiredSentinel fill(final int seed) {
    enumProp = POJOEnum.values()[seed % POJOEnum.values().length];
    longProp = 123_456_789L + seed;
    intProp = 42 + seed;
    stringProp = BufferUtil.wrapString("benchmark test string " + seed);
    binaryProp = BufferUtil.wrapString("binary data " + seed);
    objectProp.foo = 987_654_321L + seed;
    return this;
  }
}
