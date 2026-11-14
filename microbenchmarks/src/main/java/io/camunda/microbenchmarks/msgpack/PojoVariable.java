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
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Plain data with a generated codec, stores the same bytes as {@link UnpackedVariable}. */
@MsgpackPojo
public final class PojoVariable extends MsgpackBufferValue<PojoVariable> {

  long key;

  @MsgpackProperty(binary = true)
  final UnsafeBuffer value = new UnsafeBuffer(Decoding.EMPTY);

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoVariable setKey(final long key) {
    this.key = key;
    return this;
  }

  public PojoVariable setValue(final DirectBuffer value) {
    this.value.wrap(BufferUtil.cloneBuffer(value));
    return this;
  }
}
