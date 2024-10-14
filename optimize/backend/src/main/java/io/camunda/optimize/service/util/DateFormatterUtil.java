/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.github.sisyphsu.dateparser.DateParserUtils;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class DateFormatterUtil {

  private static final DateTimeFormatter OPTIMIZE_FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private DateFormatterUtil() {}

  public static boolean isValidOptimizeDateFormat(final String value) {
    try {
      OffsetDateTime.parse(value, OPTIMIZE_FORMATTER);
      return true;
    } catch (final DateTimeParseException ex) {
      return false;
    }
  }

  public static Optional<String> getDateStringInOptimizeDateFormat(final String dateString) {
    try {
      final OffsetDateTime parsedOffsetDateTime = DateParserUtils.parseOffsetDateTime(dateString);
      return Optional.of(parsedOffsetDateTime.format(OPTIMIZE_FORMATTER));
    } catch (final DateTimeParseException ex) {
      return Optional.empty();
    }
  }

  public static Optional<OffsetDateTime> getOffsetDateTimeFromIsoZoneDateTimeString(
      final String dateString) {
    try {
      return Optional.of(
          ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_ZONED_DATE_TIME)
              .toOffsetDateTime());
    } catch (final DateTimeParseException ex) {
      return Optional.empty();
    }
  }
}
