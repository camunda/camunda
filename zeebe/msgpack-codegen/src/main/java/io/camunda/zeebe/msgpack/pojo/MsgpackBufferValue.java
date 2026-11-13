/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Lets a {@link MsgpackPojo} that still has to read and write itself, e.g. as a {@code DbValue},
 * delegate to its generated codec. The reader and writer are the per-thread instances of {@link
 * MsgpackScratch}.
 *
 * <p>The generated codec and ops of the concrete class are found with {@link Codecs}, so the class
 * does not have to mention them. {@link #reset()} is called before every read.
 *
 * <p>Temporary: it goes away once the code storing these objects owns the codec call and the
 * scratch state.
 *
 * @param <T> the concrete POJO type
 */
public abstract class MsgpackBufferValue<T extends MsgpackBufferValue<T>>
    implements BufferReader, BufferWriter, Recyclable {

  // resolved once per instance, so that reads and writes do not look them up
  private final MsgpackCodec<T> codec = Codecs.of(type());
  private final MsgpackOps<T> ops = Codecs.opsOf(type());

  @Override
  public int getLength() {
    return codec.getLength(self());
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return codec.write(MsgpackScratch.writer(buffer, offset), self());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();
    final var reader = MsgpackScratch.reader(buffer, offset, length);
    try {
      codec.read(reader, self());
    } catch (final RuntimeException e) {
      // the same context as UnpackedObject, which this replaces
      throw new RuntimeException(
          "Could not deserialize object [%s]. Deserialization stuck at offset %d of length %d"
              .formatted(getClass().getSimpleName(), reader.getOffset(), length),
          e);
    }
  }

  /** Restores the field initializers. Override to also reset state that is not stored. */
  @Override
  public void reset() {
    ops.reset(self());
  }

  @SuppressWarnings("unchecked")
  private Class<T> type() {
    return (Class<T>) getClass();
  }

  @SuppressWarnings("unchecked")
  private T self() {
    return (T) this;
  }
}
