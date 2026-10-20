/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.snapshot;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** The JSON of a snapshot's descriptor isn't what it should be. */
public final class InvalidSnapshotDescriptorException extends IOException {

  private final String field;

  /**
   * @param field the field that is wrong, as a path such as {@code files.0.size}
   * @param expected what the field should be
   * @param found what the field is
   */
  public InvalidSnapshotDescriptorException(
      final String field, final String expected, final String found) {
    this(field, expected, found, null);
  }

  public InvalidSnapshotDescriptorException(
      final String field,
      final String expected,
      final String found,
      @Nullable final Throwable cause) {
    super("Expected %s to be %s, but got %s".formatted(field, expected, found), cause);
    this.field = field;
  }

  /** The field that is wrong, as a path such as {@code files.0.size}. */
  public String field() {
    return field;
  }
}
