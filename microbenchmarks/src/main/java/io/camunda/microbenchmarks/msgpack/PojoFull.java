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
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Plain data with every field type the generator supports and a generated codec. It stores the same
 * bytes as {@link UnpackedFull}.
 */
@MsgpackPojo
public final class PojoFull extends MsgpackBufferValue<PojoFull> {

  /** The msgpack encoding of the string "abc". */
  static final DirectBuffer RAW = new UnsafeBuffer(new byte[] {(byte) 0xa3, 'a', 'b', 'c'});

  long key = -1L;
  int count = 1;
  boolean flag;
  SampleKind kind = SampleKind.FIRST;
  final UnsafeBuffer text = new UnsafeBuffer(Decoding.EMPTY);

  @MsgpackProperty(binary = true)
  final UnsafeBuffer blob = new UnsafeBuffer(Decoding.EMPTY);

  @MsgpackProperty(raw = true)
  final UnsafeBuffer raw = new UnsafeBuffer(Decoding.EMPTY);

  final List<DirectBuffer> ids = new ArrayList<>();
  final PojoNested nested = new PojoNested();
  final UnpackedNested legacy = new UnpackedNested();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public PojoFull fill(final int seed) {
    key = 2_251_799_813_685_300L + seed;
    count = 1 + seed % 5;
    flag = seed % 2 == 0;
    kind = SampleKind.values()[seed % SampleKind.values().length];
    text.wrap(BufferUtil.wrapString("text-" + seed));
    blob.wrap(BufferUtil.wrapString("{\"orderId\":" + seed + "}"));
    raw.wrap(RAW);
    for (int i = 0; i < 3; i++) {
      ids.add(BufferUtil.wrapString("id-" + seed + "-" + i));
    }
    nested.fill(seed);
    legacy.fill(seed + 1);
    return this;
  }
}
