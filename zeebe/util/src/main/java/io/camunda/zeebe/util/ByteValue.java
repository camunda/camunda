/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.util.Locale;

public final class ByteValue {
  private static final int CONVERSION_FACTOR_KB = 1024;
  private static final int CONVERSION_FACTOR_MB = CONVERSION_FACTOR_KB * 1024;
  private static final int CONVERSION_FACTOR_GB = CONVERSION_FACTOR_MB * 1024;
  private static final char[] PREFIXES = {'K', 'M', 'G', 'T', 'P', 'E'};

  /**
   * Converts the {@code value} kilobytes into bytes
   *
   * @param value value in kilobytes
   * @return {@code value} converted into bytes
   */
  public static long ofKilobytes(final long value) {
    return value * CONVERSION_FACTOR_KB;
  }

  /**
   * Converts the {@code value} megabytes into bytes
   *
   * @param value value in megabytes
   * @return {@code value} converted into bytes
   */
  public static long ofMegabytes(final long value) {
    return value * CONVERSION_FACTOR_MB;
  }

  /**
   * Converts the {@code value} gigabytes into bytes
   *
   * @param value value in gigabytes
   * @return {@code value} converted into bytes
   */
  public static long ofGigabytes(final long value) {
    return value * CONVERSION_FACTOR_GB;
  }

  public static String prettyPrint(final long bytes) {
    if (bytes < 1024) {
      return bytes + "B";
    }
    int exp = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
    final double value = bytes / (double) (1L << (exp * 10));
    double rounded = (value < 10) ? Math.round(value * 10) / 10.0 : Math.round(value);

    // Handle rollover (e.g. 1,048,575 → 1024 KiB → 1 MiB)
    if (rounded >= 1024) {
      exp++;
      rounded = 1;
    }

    final var prefix = PREFIXES[exp - 1];
    final var fmt = rounded == (long) rounded ? "%.0f%ciB" : "%.1f%ciB";
    return String.format(Locale.ROOT, fmt, rounded, prefix);
  }
}
