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
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Plain data whose properties have no default, so writing and reading check that every one is set.
 * Stores the same bytes as {@link Pojo}, and {@link PojoRequiredPlain} is the same without the
 * check.
 */
@MsgpackPojo
public final class PojoRequired extends MsgpackBufferValue<PojoRequired> {
  @Override
  protected MsgpackCodec<PojoRequired> codec() {
    return PojoRequiredCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoRequired> ops() {
    return PojoRequiredOps.INSTANCE;
  }

  @Nullable POJOEnum enumProp;
  final RequiredLong longProp = new RequiredLong();
  final RequiredInt intProp = new RequiredInt();
  @Nullable DirectBuffer stringProp;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer binaryProp;

  final PojoRequiredNested objectProp = new PojoRequiredNested();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoRequired fill(final int seed) {
    enumProp = POJOEnum.values()[seed % POJOEnum.values().length];
    longProp.set(123_456_789L + seed);
    intProp.set(42 + seed);
    stringProp = BufferUtil.wrapString("benchmark test string " + seed);
    binaryProp = BufferUtil.wrapString("binary data " + seed);
    objectProp.foo = 987_654_321L + seed;
    return this;
  }
}
