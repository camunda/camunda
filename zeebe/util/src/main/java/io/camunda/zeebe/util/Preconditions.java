/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.function.Consumer;

public final class Preconditions {
  private Preconditions() {}

  public static void assertPositive(final long number, final String fieldName) {
    if (number <= 0) {
      throw new IllegalArgumentException(
          String.format("Expected %s to be positive if set, but got %d", fieldName, number));
    }
  }

  public static Consumer<Integer> assertPositive(final String fieldName) {
    return (number -> assertPositive(number, fieldName));
  }

  public static void assertNonEmpty(final String s, final String fieldName) {
    if (s.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Expected %s to be a non empty string if Optional is not empty, but got an empty string",
              fieldName));
    }
  }

  public static Consumer<String> assertNonEmpty(final String fieldName) {
    return s -> assertNonEmpty(s, fieldName);
  }
}
