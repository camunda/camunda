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
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

/** Plain data nested in {@link PojoFull}, stores the same bytes as {@link UnpackedNested}. */
@MsgpackPojo
public final class PojoNested extends MsgpackBufferValue<PojoNested> {

  long id = -1L;
  final UnsafeBuffer label = new UnsafeBuffer(Decoding.EMPTY);

  public PojoNested fill(final int seed) {
    id = seed;
    label.wrap(BufferUtil.wrapString("label-" + seed));
    return this;
  }
}
