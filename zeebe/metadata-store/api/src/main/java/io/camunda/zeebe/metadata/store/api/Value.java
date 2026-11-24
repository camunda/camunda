/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * An opaque, immutable value of a key: the consumer serializes it before a write and deserializes
 * it after a read. Two values are equal if they hold the same bytes, as the store compares them.
 */
public final class Value {

  private final byte[] bytes;

  private Value(final byte[] bytes) {
    this.bytes = bytes;
  }

  /** Returns a value holding a copy of the bytes. */
  public static Value of(final byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return new Value(bytes.clone());
  }

  /** Returns a copy of the bytes. */
  public byte[] toByteArray() {
    return bytes.clone();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final Value other && Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return Arrays.toString(bytes);
  }
}
