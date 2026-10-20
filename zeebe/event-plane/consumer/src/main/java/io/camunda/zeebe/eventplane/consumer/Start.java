/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.util.Locale;

/** Where a reader without a cursor starts. */
public enum Start implements StartFrom {
  /** At the first entry that the store holds of the log's current path. */
  EARLIEST,
  /** After the last entry of the newest segment, as the reader's first poll finds it. */
  LATEST;

  /** The name in a group's JSON: {@code earliest} or {@code latest}. */
  public String jsonName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * The start that a group's JSON names.
   *
   * @throws IllegalArgumentException if the name is neither {@code earliest} nor {@code latest}
   */
  public static Start ofJsonName(final String name) {
    for (final var start : values()) {
      if (start.jsonName().equals(name)) {
        return start;
      }
    }
    throw new IllegalArgumentException(
        "Expected a start of 'earliest' or 'latest', but got '%s'".formatted(name));
  }
}
