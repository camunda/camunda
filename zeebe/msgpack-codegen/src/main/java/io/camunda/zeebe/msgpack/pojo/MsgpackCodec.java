/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;

/**
 * Stateless (de)serializer of {@code T} to and from msgpack. The caller owns the writer and the
 * reader, typically one of each per thread, so a codec can be shared freely.
 *
 * <p>Values are filled in place. Strings, binaries and raw values are not copied: they are views on
 * the source buffer, only valid while it is not reused. Nested objects are owned by their parent
 * and filled in place as well. Resetting is the job of the value, see {@link Recyclable}.
 */
public interface MsgpackCodec<T extends Recyclable> {

  /** Writes the value and returns the number of bytes written. */
  int write(MsgPackWriter writer, T value);

  /** The number of bytes {@link #write} produces for the value. */
  int getLength(T value);

  /**
   * Reads the properties present in the input into {@code target}. Properties missing in the input
   * keep their current value, so call {@link Recyclable#reset()} on the target first.
   */
  void read(MsgPackReader reader, T target);
}
