/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import java.util.Objects;

/**
 * A precondition on the one key of its write, or of its {@link Write.Check}. Conditions compare the
 * current value only; the previous value is ignored. {@link #none()} is the condition of an
 * unconditional write.
 */
public sealed interface Condition {

  /** Always holds: the write is unconditional. Not allowed on a {@link Write.Check}. */
  static Condition none() {
    return new None();
  }

  /** The key holds no value. */
  static Condition absent() {
    return new Absent();
  }

  /** The key holds a value. */
  static Condition exists() {
    return new Exists();
  }

  /**
   * The current value is exactly this value. Pass the value that was read, never a value serialized
   * again: the same object does not always serialize to the same bytes.
   */
  static Condition valueEquals(final StoreValue expected) {
    return new ValueEquals(expected);
  }

  record None() implements Condition {}

  record Absent() implements Condition {}

  record Exists() implements Condition {}

  record ValueEquals(StoreValue expected) implements Condition {

    public ValueEquals {
      Objects.requireNonNull(expected, "expected");
    }
  }
}
