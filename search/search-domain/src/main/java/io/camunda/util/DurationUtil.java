/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.time.Duration;

public final class DurationUtil {

  private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();
  private static final long MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();
  private static final long MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis();
  private static final long MILLIS_PER_SECOND = Duration.ofSeconds(1).toMillis();

  private DurationUtil() {}

  /** Returns the larger of two durations. */
  public static Duration max(final Duration a, final Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  /** Returns the smaller of two durations. */
  public static Duration min(final Duration a, final Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  public static String toEsOsInterval(final Duration duration) {
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException("Duration must be greater than zero: " + duration);
    }

    final long millis = duration.toMillis();
    if (isWholeMultiple(millis, MILLIS_PER_DAY)) {
      return (millis / MILLIS_PER_DAY) + "d";
    }
    if (isWholeMultiple(millis, MILLIS_PER_HOUR)) {
      return (millis / MILLIS_PER_HOUR) + "h";
    }
    if (isWholeMultiple(millis, MILLIS_PER_MINUTE)) {
      return (millis / MILLIS_PER_MINUTE) + "m";
    }
    if (isWholeMultiple(millis, MILLIS_PER_SECOND)) {
      return (millis / MILLIS_PER_SECOND) + "s";
    }
    return millis + "ms";
  }

  private static boolean isWholeMultiple(final long value, final long unit) {
    return value > 0 && value % unit == 0;
  }
}
