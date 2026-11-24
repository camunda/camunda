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
 * The bytes of one value, immutable: the bytes are copied when the value is built and when they are
 * read, so no caller can change a value after the store or another caller has it. Equality compares
 * the bytes, as the store does. Values are opaque: the consumer serializes a value before it writes
 * it and deserializes it after a read.
 */
public final class StoreValue {

  private final byte[] bytes;

  private StoreValue(final byte[] bytes) {
    this.bytes = bytes;
  }

  /** Returns a value holding a copy of the bytes. */
  public static StoreValue of(final byte[] bytes) {
    return new StoreValue(Objects.requireNonNull(bytes, "bytes").clone());
  }

  /** Returns a copy of the bytes. */
  public byte[] toByteArray() {
    return bytes.clone();
  }

  public int size() {
    return bytes.length;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final StoreValue other && Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "StoreValue" + Arrays.toString(bytes);
  }
}
