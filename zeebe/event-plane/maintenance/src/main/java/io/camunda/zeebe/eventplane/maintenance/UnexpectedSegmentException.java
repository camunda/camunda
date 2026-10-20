/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

/** A stored segment that holds something other than its key says. */
final class UnexpectedSegmentException extends RuntimeException {

  private final String key;

  UnexpectedSegmentException(final String key, final String expected, final String got) {
    super("Expected the segment '%s' to have %s, but got %s".formatted(key, expected, got));
    this.key = key;
  }

  UnexpectedSegmentException(
      final String key, final String expected, final String got, final Throwable cause) {
    super("Expected the segment '%s' to have %s, but got %s".formatted(key, expected, got), cause);
    this.key = key;
  }

  /** The key of the segment. */
  String key() {
    return key;
  }
}
