/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.microbenchmarks.msgpack.Pojo.POJOEnum;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * {@link PojoRequired} with defaults instead of unset properties, so nothing is checked. Stores the
 * same bytes as {@link Pojo}.
 */
@MsgpackPojo
public final class PojoRequiredPlain extends MsgpackBufferValue<PojoRequiredPlain> {
  @Override
  protected MsgpackCodec<PojoRequiredPlain> codec() {
    return PojoRequiredPlainCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PojoRequiredPlain> ops() {
    return PojoRequiredPlainOps.INSTANCE;
  }

  POJOEnum enumProp = POJOEnum.FOO;
  long longProp;
  int intProp;
  DirectBuffer stringProp = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer binaryProp = Decoding.EMPTY;

  final PojoRequiredNested objectProp = new PojoRequiredNested();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoRequiredPlain fill(final int seed) {
    enumProp = POJOEnum.values()[seed % POJOEnum.values().length];
    longProp = 123_456_789L + seed;
    intProp = 42 + seed;
    stringProp = BufferUtil.wrapString("benchmark test string " + seed);
    binaryProp = BufferUtil.wrapString("binary data " + seed);
    objectProp.foo = 987_654_321L + seed;
    return this;
  }
}
