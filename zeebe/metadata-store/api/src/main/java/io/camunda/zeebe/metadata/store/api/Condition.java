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
 * A precondition on the one key of its write, or of its {@link Write.Check}. Conditions compare the
 * current value only; the previous value is ignored.
 */
public sealed interface Condition {

  /** The key holds no value. */
  static Condition absent() {
    return new Absent();
  }

  /** The key holds a value. */
  static Condition exists() {
    return new Exists();
  }

  /**
   * The current value has exactly these bytes. Pass the array that was read, never a value
   * serialized again.
   */
  static Condition valueEquals(final byte[] expected) {
    return new ValueEquals(expected);
  }

  record Absent() implements Condition {}

  record Exists() implements Condition {}

  /** Equality compares the expected bytes. */
  record ValueEquals(byte[] expected) implements Condition {

    public ValueEquals {
      Objects.requireNonNull(expected, "expected");
    }

    @Override
    public boolean equals(final Object o) {
      return o instanceof ValueEquals(final byte[] expected1) && Arrays.equals(expected, expected1);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(expected);
    }

    @Override
    public String toString() {
      return "ValueEquals[expected=%s]".formatted(Arrays.toString(expected));
    }
  }
}
