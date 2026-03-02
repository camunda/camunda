/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public final class DurationUtil {

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
    final long seconds = duration.getSeconds();
    if (seconds <= 0) {
      throw new IllegalArgumentException("Duration must be greater than zero seconds: " + duration);
    }

    if (seconds % ChronoUnit.YEARS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.YEARS.getDuration().getSeconds()) + "y";
    } else if (seconds % ChronoUnit.MONTHS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.MONTHS.getDuration().getSeconds()) + "M";
    } else if (seconds % ChronoUnit.WEEKS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.WEEKS.getDuration().getSeconds()) + "w";
    } else if (seconds % ChronoUnit.DAYS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.DAYS.getDuration().getSeconds()) + "d";
    } else if (seconds % ChronoUnit.HOURS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.HOURS.getDuration().getSeconds()) + "h";
    } else if (seconds % ChronoUnit.MINUTES.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.MINUTES.getDuration().getSeconds()) + "m";
    } else if (seconds % ChronoUnit.SECONDS.getDuration().getSeconds() == 0) {
      return (seconds / ChronoUnit.SECONDS.getDuration().getSeconds()) + "s";
    } else {
      throw new IllegalArgumentException("Duration must be a whole number of seconds: " + duration);
    }
  }
}
