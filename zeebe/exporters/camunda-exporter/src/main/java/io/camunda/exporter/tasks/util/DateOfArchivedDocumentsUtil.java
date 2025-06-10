/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DateOfArchivedDocumentsUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(DateOfArchivedDocumentsUtil.class);
  private static final Pattern TEMPORAL_PATTERN = Pattern.compile("(\\d+)" + "([smhdwMy])");

  public static String getDateOfDocumentsInArchiveBatch(
      final String endDate,
      String dateOfMostRecentArchiveIndex,
      final String rolloverInterval,
      final String elsRolloverDateFormat) {

    // if historicalDate is not set, or rolloverInterval is not valid,
    // we default to the endDate.
    if (dateOfMostRecentArchiveIndex == null
        || dateOfMostRecentArchiveIndex.isEmpty()
        || rolloverInterval == null
        || rolloverInterval.isEmpty()) {
      return endDate;
    }

    final LocalDateTime endDateTime = parseFlexibleDateTime(endDate, elsRolloverDateFormat);
    final LocalDateTime lastEndDate =
        parseFlexibleDateTime(dateOfMostRecentArchiveIndex, elsRolloverDateFormat);

    final Temp temporalAmount = parseTemporalAmount(rolloverInterval);

    final LocalDateTime rollover =
        lastEndDate.plus(temporalAmount.amount, temporalAmount.chronoUnit);
    try {
      if (endDateTime.isAfter(rollover)) {
        // If the end date is after the last historical archiver date plus
        // the rollover, then a new one needs to be set.
        final var previousDate = dateOfMostRecentArchiveIndex;
        dateOfMostRecentArchiveIndex = endDate;
        LOGGER.debug(
            "Rolling over historical archive date from {} date to: {}",
            previousDate,
            dateOfMostRecentArchiveIndex);
      }

    } catch (final IllegalArgumentException e) {
      LOGGER.error(
          "Error parsing rollover interval '{}'. Please check the format. Using the last historical date: {}",
          rolloverInterval,
          dateOfMostRecentArchiveIndex,
          e);
    }

    return dateOfMostRecentArchiveIndex;
  }

  private static Temp parseTemporalAmount(final String input) throws IllegalArgumentException {
    final Matcher matcher = TEMPORAL_PATTERN.matcher(input);

    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid format: " + input);
    }

    final int amount = Integer.parseInt(matcher.group(1));
    final String unit = matcher.group(2);

    return switch (unit) {
      case "h" -> new Temp(amount, ChronoUnit.HOURS);
      case "d" -> new Temp(amount, ChronoUnit.DAYS);
      case "w" -> new Temp(amount * 7, ChronoUnit.DAYS);
      case "M" -> new Temp(amount, ChronoUnit.MONTHS);
      default -> throw new IllegalArgumentException("Unsupported time amount: " + unit);
    };
  }

  private static LocalDateTime parseFlexibleDateTime(final String dateStr, final String pattern) {
    try {
      if ("date".equals(pattern)) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return LocalDate.parse(dateStr, formatter).atStartOfDay();
      } else {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(dateStr, formatter);
      }
    } catch (final Exception exception) {
      throw new IllegalArgumentException(
          "Invalid date format: " + dateStr + " with pattern: " + pattern, exception);
    }
  }

  private record Temp(int amount, ChronoUnit chronoUnit) {}
}
