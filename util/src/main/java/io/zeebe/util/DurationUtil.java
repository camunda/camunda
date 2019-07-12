/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class DurationUtil {
  /**
   * Input format expected to be [value][unit], where: - value is a number {@link
   * java.lang.Float#parseFloat} - unit is one of: 'ms', 's', 'm', 'h'
   */
  public static Duration parse(String durationString) {
    final String matchedUnit = extractUnit(durationString);
    final String matchedValue =
        durationString.substring(0, durationString.length() - matchedUnit.length());
    if (matchedValue.isEmpty()) {
      throw new IllegalArgumentException(
          "no value given; expected format: [value][unit], e.g. '1s', '1.5ms'");
    }

    final long value;
    final ChronoUnit unit;

    // TODO: could also be a static Map
    switch (matchedUnit) {
      case "ms":
      case "":
        unit = ChronoUnit.MICROS;
        value = (long) (Float.parseFloat(matchedValue) * 1000);
        break;
      case "s":
        unit = ChronoUnit.MILLIS;
        value = (long) (Float.parseFloat(matchedValue) * 1000);
        break;
      case "m":
        unit = ChronoUnit.SECONDS;
        value = (long) (Float.parseFloat(matchedValue) * 60);
        break;
      case "h":
        unit = ChronoUnit.MINUTES;
        value = (long) (Float.parseFloat(matchedValue) * 60);
        break;
      default:
        final String errorMessage =
            String.format("unknown unit %s; must be one of: ms, s, m, h", matchedUnit);
        throw new IllegalArgumentException(errorMessage);
    }

    return Duration.of(value, unit);
  }

  private static String extractUnit(CharSequence humanReadable) {
    final StringBuilder unitBuilder = new StringBuilder();
    for (int i = humanReadable.length() - 1; i >= 0; i--) {
      final char current = humanReadable.charAt(i);
      if (Character.isAlphabetic(current)) {
        unitBuilder.append(current);
      } else {
        break;
      }
    }

    return unitBuilder.reverse().toString();
  }
}
