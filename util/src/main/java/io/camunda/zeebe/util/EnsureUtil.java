/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import java.util.Objects;
import org.agrona.DirectBuffer;

public final class EnsureUtil {

  private static final String ERROR_MSG_NON_EMPTY = "%s must not be empty";

  private EnsureUtil() {}

  public static void ensureNotNull(final String property, final Object o) {
    Objects.requireNonNull(o, property);
  }

  public static void ensureGreaterThan(
      final String property, final long testValue, final long comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new IllegalArgumentException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureGreaterThanOrEqual(
      final String property, final long testValue, final long comparisonValue) {
    if (testValue < comparisonValue) {
      throw new IllegalArgumentException(
          property + " must be greater than or equal to " + comparisonValue);
    }
  }

  public static void ensureGreaterThan(
      final String property, final double testValue, final double comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new IllegalArgumentException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureGreaterThanOrEqual(
      final String property, final double testValue, final double comparisonValue) {
    if (testValue < comparisonValue) {
      throw new IllegalArgumentException(
          property + " must be greater than or equal to " + comparisonValue);
    }
  }

  public static void ensureNotNullOrEmpty(final String property, final String testValue) {
    ensureNotNull(property, testValue);

    if (testValue.isEmpty()) {
      throw new IllegalArgumentException(String.format(ERROR_MSG_NON_EMPTY, property));
    }
  }

  public static void ensureNotNullOrEmpty(final String property, final byte[] testValue) {
    ensureNotNull(property, testValue);

    if (testValue.length == 0) {
      throw new IllegalArgumentException(String.format(ERROR_MSG_NON_EMPTY, property));
    }
  }

  public static void ensureNotNullOrEmpty(final String property, final DirectBuffer testValue) {
    ensureNotNull(property, testValue);

    if (testValue.capacity() == 0) {
      throw new IllegalArgumentException(String.format(ERROR_MSG_NON_EMPTY, property));
    }
  }
}
