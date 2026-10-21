/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import org.jspecify.annotations.Nullable;

/** Text that {@link LogId#parse} can't read as a log, {@code {cluster}/{tenant}/{partition}}. */
public final class InvalidLogIdException extends IllegalArgumentException {

  private static final String EXPECTED =
      "a log as {cluster}/{tenant}/{partition}, with a partition of 1 or more";

  private final String text;

  InvalidLogIdException(final String text, final @Nullable Throwable cause) {
    super("Expected %s, but got '%s'".formatted(EXPECTED, text), cause);
    this.text = text;
  }

  /** What the text should have been. */
  public String expected() {
    return EXPECTED;
  }

  /** The text that was rejected. */
  public String text() {
    return text;
  }
}
