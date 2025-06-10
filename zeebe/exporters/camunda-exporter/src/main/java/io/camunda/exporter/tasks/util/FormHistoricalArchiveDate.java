/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormHistoricalArchiveDate {
  private static final Logger LOGGER = LoggerFactory.getLogger(FormHistoricalArchiveDate.class);
  private static final Pattern TEMPORAL_PATTERN = Pattern.compile("(\\d+)" + "([smhdwMy])");

  @VisibleForTesting
  public static String getHistoricalArchiverDate(
      final String endDate,
      String historicalDate,
      final String rolloverInterval,
      final String elsRolloverDateFormat) {

    // if historicalDate is not set, or rolloverInterval is not valid,
    // we default to the endDate.
    if (historicalDate == null
        || historicalDate.isEmpty()
        || rolloverInterval == null
        || rolloverInterval.isEmpty()) {
      return endDate;
    }
    final CalendarInterval calendarInterval = mapCalendarInterval(rolloverInterval);

    if (calendarInterval == CalendarInterval.Minute
        || calendarInterval == CalendarInterval.Second) {
      // For minute and seconds, we use date as is, minimum amount should be hours.
      return endDate;
    }

    final LocalDateTime endDateTime = parseFlexibleDateTime(endDate, elsRolloverDateFormat);
    final LocalDateTime lastEndDate = parseFlexibleDateTime(historicalDate, elsRolloverDateFormat);

    final Temp temporalAmount = parseTemporalAmount(rolloverInterval);

    final LocalDateTime rollover =
        lastEndDate.plus(temporalAmount.amount, temporalAmount.chronoUnit);
    try {
      if (endDateTime.isAfter(rollover)) {
        // If the end date is after the last historical archiver date plus
        // the rollover, then a new one needs to be set.
        final var previousDate = historicalDate;
        historicalDate = endDate;
        LOGGER.debug(
            "Rolling over historical archive date from {} date to: {}",
            previousDate,
            historicalDate);
      }

    } catch (final IllegalArgumentException e) {
      LOGGER.error(
          "Error parsing rollover interval '{}'. Please check the format. Using the last historical date: {}",
          rolloverInterval,
          historicalDate,
          e);
    }

    return historicalDate;
  }

  private static CalendarInterval mapCalendarInterval(final String alias) {
    return Arrays.stream(CalendarInterval.values())
        .filter(c -> c.aliases() != null)
        .filter(
            c ->
                Arrays.stream(c.aliases())
                    .anyMatch(e -> e.endsWith(alias.substring(alias.length() - 1))))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported rollover interval alias: " + alias));
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

  public static LocalDateTime parseFlexibleDateTime(final String dateStr, final String pattern) {
    try {
      if ("yyyy-MM-dd".equals(pattern) || "date".equals(pattern)) {
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
