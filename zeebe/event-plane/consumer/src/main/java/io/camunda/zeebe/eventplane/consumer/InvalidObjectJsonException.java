/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** The JSON of a group object or a retention marker isn't what it should be. */
public final class InvalidObjectJsonException extends IOException {

  private final String field;

  InvalidObjectJsonException(final String field, final String expected, final String found) {
    this(field, expected, found, null);
  }

  InvalidObjectJsonException(
      final String field,
      final String expected,
      final String found,
      @Nullable final Throwable cause) {
    super("Expected %s to be %s, but got %s".formatted(field, expected, found), cause);
    this.field = field;
  }

  /** The field that is wrong, as a path such as {@code subscription[0].tenant}. */
  public String field() {
    return field;
  }
}
