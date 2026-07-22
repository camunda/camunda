/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Manual length-prefixed field packing for the {@link OpenInstanceValue}/{@link OpenElementValue}
 * flyweights. Strings ride as {@code int length ++ utf8 bytes}; scalars ride raw. Write and read
 * use agrona's native byte order — the only constraint is that both sides agree, which they do
 * because a value is only ever read back by the same code that wrote it.
 */
final class LakeValueCodec {

  private LakeValueCodec() {}

  /** Serialized size of a length-prefixed byte field. */
  static int sizeOf(final byte[] field) {
    return Integer.BYTES + field.length;
  }

  /** Writes {@code field} length-prefixed at {@code offset}; returns the offset past it. */
  static int putBytes(final MutableDirectBuffer buffer, int offset, final byte[] field) {
    buffer.putInt(offset, field.length);
    offset += Integer.BYTES;
    buffer.putBytes(offset, field);
    return offset + field.length;
  }

  /** Reads a length-prefixed byte field at {@code offset}. */
  static byte[] getBytes(final DirectBuffer buffer, final int offset) {
    final int length = buffer.getInt(offset);
    final byte[] field = new byte[length];
    buffer.getBytes(offset + Integer.BYTES, field);
    return field;
  }
}
