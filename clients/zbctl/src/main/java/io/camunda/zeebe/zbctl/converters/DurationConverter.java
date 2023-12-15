/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.converters;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import picocli.CommandLine.ITypeConverter;

public final class DurationConverter implements ITypeConverter<Duration> {
  private static final Map<String, ChronoUnit> UNITS =
      Map.of(
          "ns", ChronoUnit.NANOS,
          "ms", ChronoUnit.MILLIS,
          "s", ChronoUnit.SECONDS,
          "m", ChronoUnit.MINUTES,
          "h", ChronoUnit.HOURS,
          "d", ChronoUnit.DAYS);

  @Override
  public Duration convert(final String value) throws Exception {
    final String matchedUnit = extractUnit(value);
    final String matchedValue = value.substring(0, value.length() - matchedUnit.length());
    if (matchedValue.isEmpty()) {
      throw new IllegalArgumentException(
          "no value given; expected format: [value][unit], e.g. '1s', '1.5ms'");
    }

    final var unit = matchedUnit.isBlank() ? ChronoUnit.MILLIS : UNITS.get(matchedUnit);
    if (unit == null) {
      final String errorMessage =
          String.format("Expected one of [%s], but got %s", UNITS.keySet(), matchedUnit);
      throw new IllegalArgumentException(errorMessage);
    }

    return Duration.of(Long.parseLong(matchedValue), unit);
  }

  private String extractUnit(final CharSequence humanReadable) {
    final var unitBuilder = new StringBuilder();
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
