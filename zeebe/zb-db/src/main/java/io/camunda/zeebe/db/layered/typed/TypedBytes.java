/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.typed;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.concurrent.UnsafeBuffer;

/** Serialization glue between {@link BufferWriter} flyweights and the layered store's raw bytes. */
final class TypedBytes {

  private TypedBytes() {}

  /**
   * Serializes the writer into a fresh array. The array must be fresh on every call: the layered
   * store retains key arrays in its in-memory layers, so a reused scratch buffer would corrupt
   * previously written entries.
   */
  static byte[] serialize(final BufferWriter writer) {
    final byte[] bytes = new byte[writer.getLength()];
    writer.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }

  static <T extends BufferReader> T wrapInto(final T reader, final byte[] bytes) {
    reader.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return reader;
  }
}
