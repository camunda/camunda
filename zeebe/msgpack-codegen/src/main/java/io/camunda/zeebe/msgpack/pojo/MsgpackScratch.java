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

/**
 * Per-thread reader and writer for callers that cannot own them, e.g. classes that still have to
 * read and write themselves. Prefer passing an explicitly owned reader and writer to a {@link
 * MsgpackCodec}.
 *
 * <p>The returned instances are reused by the next call on the same thread: use them for one codec
 * call and do not keep them. Codecs never call back into this class, so calls cannot overlap.
 */
public final class MsgpackScratch {

  private static final ThreadLocal<MsgPackWriter> WRITER =
      ThreadLocal.withInitial(MsgPackWriter::new);
  private static final ThreadLocal<MsgPackReader> READER =
      ThreadLocal.withInitial(MsgPackReader::new);

  private MsgpackScratch() {}

  public static MsgPackWriter writer(final MutableDirectBuffer buffer, final int offset) {
    return WRITER.get().wrap(buffer, offset);
  }

  public static MsgPackReader reader(
      final DirectBuffer buffer, final int offset, final int length) {
    return READER.get().wrap(buffer, offset, length);
  }
}
