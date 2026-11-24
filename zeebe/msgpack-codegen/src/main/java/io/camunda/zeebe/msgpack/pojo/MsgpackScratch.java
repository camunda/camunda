/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Per-thread reader and writer for callers that cannot own them, e.g. classes that still have to
 * read and write themselves. Prefer passing an explicitly owned reader and writer to a {@link
 * MsgpackCodec}.
 *
 * <p>Use it in a try-with-resources block around one codec call:
 *
 * <pre>{@code
 * try (final var scratch = MsgpackScratch.acquire()) {
 *   return codec.write(scratch.writer(buffer, offset), value);
 * }
 * }</pre>
 *
 * <p>{@link #acquire()} returns the same instance on each call on a thread, so it does not
 * allocate. The reader and writer are reused by the next call: do not keep them. Codecs never call
 * back into this class, so calls cannot overlap.
 *
 * <p>{@link #close()} wraps the reader and writer around an empty buffer, so that the thread does
 * not keep the caller's buffer reachable until its next call. Values read before the close stay
 * valid: a decoded view wraps the source buffer, not the reader.
 */
public final class MsgpackScratch implements AutoCloseable {

  private static final UnsafeBuffer EMPTY = new UnsafeBuffer(new byte[0]);
  private static final ThreadLocal<MsgpackScratch> CURRENT =
      ThreadLocal.withInitial(MsgpackScratch::new);

  private final MsgPackWriter writer = new MsgPackWriter();
  private final MsgPackReader reader = new MsgPackReader();

  private MsgpackScratch() {}

  /** Returns the scratch instance of this thread. */
  public static MsgpackScratch acquire() {
    return CURRENT.get();
  }

  public MsgPackWriter writer(final MutableDirectBuffer buffer, final int offset) {
    return writer.wrap(buffer, offset);
  }

  public MsgPackReader reader(final DirectBuffer buffer, final int offset, final int length) {
    return reader.wrap(buffer, offset, length);
  }

  /** Wraps the reader and the writer around an empty buffer. */
  @Override
  public void close() {
    writer.wrap(EMPTY, 0);
    reader.wrap(EMPTY, 0, 0);
  }
}
