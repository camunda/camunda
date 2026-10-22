/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

/** An entry appended to a segment that doesn't follow the previous one in the journal. */
public final class EntryOutOfOrderException extends IllegalArgumentException {

  private final long expected;
  private final long actual;

  private EntryOutOfOrderException(String what, long expected, long actual) {
    super("Expected %s %d, but got %d".formatted(what, expected, actual));
    this.expected = expected;
    this.actual = actual;
  }

  static EntryOutOfOrderException index(long expected, long actual) {
    return new EntryOutOfOrderException("an entry at index", expected, actual);
  }

  static EntryOutOfOrderException position(long expected, long actual) {
    return new EntryOutOfOrderException(
        "an entry whose first record is at position", expected, actual);
  }

  /** The index or position that would have followed. */
  public long expected() {
    return expected;
  }

  /** The entry's index or first position. */
  public long actual() {
    return actual;
  }
}
