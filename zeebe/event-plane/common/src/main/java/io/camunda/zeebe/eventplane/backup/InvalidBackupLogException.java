/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.backup;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** The JSON of a backup's log isn't what it should be. */
public final class InvalidBackupLogException extends IOException {

  private final String field;

  /**
   * @param field the field that is wrong
   * @param expected what the field should be
   * @param found what the field is
   */
  public InvalidBackupLogException(final String field, final String expected, final String found) {
    this(field, expected, found, null);
  }

  public InvalidBackupLogException(
      final String field,
      final String expected,
      final String found,
      @Nullable final Throwable cause) {
    super("Expected %s to be %s, but got %s".formatted(field, expected, found), cause);
    this.field = field;
  }

  /** The field that is wrong. */
  public String field() {
    return field;
  }
}
