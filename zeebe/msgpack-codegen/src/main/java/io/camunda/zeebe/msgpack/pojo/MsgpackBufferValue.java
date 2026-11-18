/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Lets a {@link MsgpackPojo} that still has to read and write itself, e.g. as a {@code DbValue},
 * delegate to its generated codec. The reader and writer are the per-thread instances of {@link
 * MsgpackScratch}.
 *
 * <p>The concrete class returns its generated codec and ops from {@link #codec()} and {@link
 * #ops()}, which only return static instances: they must not be called from the constructor. {@link
 * #reset()} is called before every read.
 *
 * <p>Temporary: it goes away once the code storing these objects owns the codec call and the
 * scratch state.
 *
 * @param <T> the concrete POJO type
 */
public abstract class MsgpackBufferValue<T extends MsgpackBufferValue<T>>
    implements BufferReader, BufferWriter, Recyclable {

  /** The generated {@code <Name>Codec}: return {@code <Name>Codec.INSTANCE}. */
  protected abstract MsgpackCodec<T> codec();

  /** The generated {@code <Name>Ops}: return {@code <Name>Ops.INSTANCE}. */
  protected abstract MsgpackOps<T> ops();

  @Override
  public int getLength() {
    return codec().getLength(self());
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    try (final var scratch = MsgpackScratch.acquire()) {
      return codec().write(scratch.writer(buffer, offset), self());
    }
  }

  /** Reads the whole buffer, like {@code UnpackedObject#wrap(DirectBuffer)}. */
  public void wrap(final DirectBuffer buffer) {
    wrap(buffer, 0, buffer.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    try (final var scratch = MsgpackScratch.acquire()) {
      final var reader = scratch.reader(buffer, offset, length);
      try {
        codec().read(reader, self());
      } catch (final RuntimeException e) {
        // the same context as UnpackedObject, which this replaces
        throw new RuntimeException(
            "Could not deserialize object [%s]. Deserialization stuck at offset %d of length %d"
                .formatted(getClass().getSimpleName(), reader.getOffset(), length),
            e);
      }
    }
  }

  /** Restores the field initializers. Override to also reset state that is not stored. */
  @Override
  public void reset() {
    ops().reset(self());
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }
}
