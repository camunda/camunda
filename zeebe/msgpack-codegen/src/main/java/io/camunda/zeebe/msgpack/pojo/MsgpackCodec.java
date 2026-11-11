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

/**
 * Stateless (de)serializer of {@code T} to and from msgpack. The caller owns the writer and the
 * reader, typically one of each per thread, so a codec can be shared freely.
 *
 * <p>Values are filled in place: strings and raw values are copied out of the source buffer, nested
 * objects are owned by their parent and filled in place as well.
 */
public interface MsgpackCodec<T> {

  /** Writes the value and returns the number of bytes written. */
  int write(MsgPackWriter writer, T value);

  /** The number of bytes {@link #write} produces for the value. */
  int getLength(T value);

  /** Sets every property of {@code target} to its default and drops unknown properties. */
  void reset(T target);

  /**
   * Reads the properties present in the input into {@code target}. Properties missing in the input
   * keep their current value, so the target is expected to be {@link #reset} first; see {@link
   * #decode}.
   */
  void read(MsgPackReader reader, T target);

  /** Resets {@code target} and reads the value the reader is positioned at into it. */
  default void decode(final MsgPackReader reader, final T target) {
    reset(target);
    read(reader, target);
  }
}
