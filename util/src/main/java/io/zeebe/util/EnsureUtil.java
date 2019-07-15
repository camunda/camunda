/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.time.Duration;
import org.agrona.DirectBuffer;

public class EnsureUtil {

  public static void ensureNotNull(String property, Object o) {
    if (o == null) {
      throw new RuntimeException(property + " must not be null");
    }
  }

  public static void ensureNotEmpty(String property, String value) {
    if (value.isEmpty()) {
      throw new RuntimeException(property + " must not be empty");
    }
  }

  public static void ensureGreaterThan(String property, long testValue, long comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new RuntimeException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureGreaterThanOrEqual(
      String property, long testValue, long comparisonValue) {
    if (testValue < comparisonValue) {
      throw new RuntimeException(property + " must be greater than or equal to " + comparisonValue);
    }
  }

  public static void ensureLessThan(String property, long testValue, long comparisonValue) {
    if (testValue >= comparisonValue) {
      throw new RuntimeException(property + " must be less than " + comparisonValue);
    }
  }

  public static void ensureLessThanOrEqual(String property, long testValue, long comparisonValue) {
    if (testValue > comparisonValue) {
      throw new RuntimeException(property + " must be less than or equal to " + comparisonValue);
    }
  }

  public static void ensureGreaterThan(String property, double testValue, double comparisonValue) {
    if (testValue <= comparisonValue) {
      throw new RuntimeException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureGreaterThanOrEqual(
      String property, double testValue, double comparisonValue) {
    if (testValue < comparisonValue) {
      throw new RuntimeException(property + " must be greater than or equal to " + comparisonValue);
    }
  }

  public static void ensureLessThan(String property, double testValue, double comparisonValue) {
    if (testValue >= comparisonValue) {
      throw new RuntimeException(property + " must be less than " + comparisonValue);
    }
  }

  public static void ensureLessThanOrEqual(
      String property, double testValue, double comparisonValue) {
    if (testValue > comparisonValue) {
      throw new RuntimeException(property + " must be less than or equal to " + comparisonValue);
    }
  }

  public static void ensureNotNullOrGreaterThan(
      String property, Duration testValue, Duration comparisonValue) {
    ensureNotNull(property, testValue);

    if (testValue.compareTo(comparisonValue) <= 0) {
      throw new RuntimeException(property + " must be greater than " + comparisonValue);
    }
  }

  public static void ensureNotNullOrGreaterThanOrEqual(
      String property, Duration testValue, Duration comparisonValue) {
    ensureNotNull(property, testValue);

    if (testValue.compareTo(comparisonValue) < 0) {
      throw new RuntimeException(property + " must be greater than or equal to " + comparisonValue);
    }
  }

  public static void ensureNotNullOrEmpty(String property, String testValue) {
    ensureNotNull(property, testValue);

    if (testValue.isEmpty()) {
      throw new RuntimeException(property + " must not be empty");
    }
  }

  public static void ensureNotNullOrEmpty(String property, byte[] testValue) {
    ensureNotNull(property, testValue);

    if (testValue.length == 0) {
      throw new RuntimeException(property + " must not be empty");
    }
  }

  public static void ensureNotNullOrEmpty(String property, DirectBuffer testValue) {
    ensureNotNull(property, testValue);

    if (testValue.capacity() == 0) {
      throw new RuntimeException(property + " must not be empty");
    }
  }

  public static void ensureAtLeastOneNotNull(String property, Object... values) {
    for (int i = 0; i < values.length; i++) {
      if (values[i] != null) {
        return;
      }
    }

    throw new RuntimeException(property + " must have at least one non-null value");
  }

  public static void ensureFalse(String property, boolean value) {
    if (value) {
      throw new RuntimeException(property + " must be false");
    }
  }

  public static void ensureArrayBacked(final DirectBuffer buffer) {
    ensureNotNull("bytes array", buffer.byteArray());
  }

  public static void ensureArrayBacked(final DirectBuffer... buffers) {
    for (final DirectBuffer buffer : buffers) {
      ensureArrayBacked(buffer);
    }
  }
}
