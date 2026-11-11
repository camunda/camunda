/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Lets a {@link MsgpackPojo} that still has to read and write itself, e.g. as a {@code DbValue},
 * delegate to its generated codec. The reader and writer are the per-thread instances of {@link
 * MsgpackScratch}.
 *
 * <p>Temporary: it goes away once the code storing these objects owns the codec call and the
 * scratch state.
 *
 * @param <T> the concrete POJO type
 */
public abstract class MsgpackBufferValue<T extends MsgpackBufferValue<T>>
    implements BufferReader, BufferWriter {

  /** The generated codec of the concrete type. */
  protected abstract MsgpackCodec<T> codec();

  @Override
  public int getLength() {
    return codec().getLength(self());
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return codec().write(MsgpackScratch.writer(buffer, offset), self());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    codec().decode(MsgpackScratch.reader(buffer, offset, length), self());
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }
}
