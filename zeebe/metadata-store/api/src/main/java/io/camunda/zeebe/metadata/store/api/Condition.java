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
 * current value only; the previous value is ignored.
 */
public sealed interface Condition {

  /** Always holds: the write is unconditional. */
  static Condition always() {
    return new Always();
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
   * The current value has exactly these bytes. Pass the value that was read, never a value
   * serialized again.
   */
  static Condition valueEquals(final Value expected) {
    return new ValueEquals(expected);
  }

  record Always() implements Condition {}

  record Absent() implements Condition {}

  record Exists() implements Condition {}

  record ValueEquals(Value expected) implements Condition {

    public ValueEquals {
      Objects.requireNonNull(expected, "expected");
    }
  }
}
