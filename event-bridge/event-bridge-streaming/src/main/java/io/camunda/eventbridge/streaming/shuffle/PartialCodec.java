/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.shuffle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Byte codec for the facts-topic payload {@link Partial}. Deterministic, exact round-trip. */
public final class PartialCodec {

  private PartialCodec() {}

  public static byte[] encode(final Partial partial) {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (final DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeInt(partial.aggId());
      out.writeInt(partial.key().length);
      out.write(partial.key());
      out.writeLong(partial.windowStart());
      out.writeInt(partial.writer());
      out.writeInt(partial.acc().length);
      out.write(partial.acc());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to encode Partial", e);
    }
    return bytes.toByteArray();
  }

  public static Partial decode(final byte[] payload) {
    try (final DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
      final int aggId = in.readInt();
      final byte[] key = new byte[in.readInt()];
      in.readFully(key);
      final long windowStart = in.readLong();
      final int writer = in.readInt();
      final byte[] acc = new byte[in.readInt()];
      in.readFully(acc);
      return new Partial(aggId, key, windowStart, writer, acc);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to decode Partial", e);
    }
  }
}
