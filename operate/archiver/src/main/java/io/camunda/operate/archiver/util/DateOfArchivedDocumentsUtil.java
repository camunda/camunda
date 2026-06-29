/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateOfArchivedDocumentsUtil {
  private static final Pattern TEMPORAL_PATTERN = Pattern.compile("(\\d+)" + "([smhdwM])");
  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateTimeFormatter DATE_ONLY_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_PATTERN);
  // a fixed reference instant used to round-trip-check a configured date format
  private static final LocalDateTime VALIDATION_SAMPLE = LocalDateTime.of(2000, 1, 2, 3, 4, 5);

  private DateOfArchivedDocumentsUtil() {}

  /** Floors {@code endDate} to the start of the rollover bucket that contains it. */
  public static String getBucketStart(
      final String endDate, final String rolloverInterval, final String dateFormat) {
    return format(bucketStartDateTime(endDate, rolloverInterval, dateFormat), dateFormat);
  }

  /**
   * Start of the bucket immediately after the one that contains {@code endDate} - i.e. the
   * exclusive upper bound of that bucket. A document belongs to the bucket if bucketStart &lt;=
   * endDate &lt; nextBucketStart.
   *
   * <p>NOTE: callers compare fetched endDate values (formatted with {@code dateFormat}) against
   * this value, so the format granularity must be at least as fine as the rollover interval. With a
   * day-only format ({@code "date"}/{@code "yyyy-MM-dd"}) and a sub-day interval (e.g. {@code
   * "4h"}) the returned value collapses to the same day as the start, the bucket appears empty, and
   * archiving would stall. Use a date-time format for sub-day rollover intervals.
   */
  public static String getNextBucketStart(
      final String endDate, final String rolloverInterval, final String dateFormat) {
    final Temp rollover = parseTemporalAmount(rolloverInterval);
    final LocalDateTime bucketStart = bucketStartDateTime(endDate, rolloverInterval, dateFormat);
    final LocalDateTime nextBucketStart =
        switch (rollover.chronoUnit()) {
          case MONTHS -> bucketStart.plusMonths(rollover.amount());
          case WEEKS -> bucketStart.plusWeeks(rollover.amount());
          default -> bucketStart.plus(Duration.of(rollover.amount(), rollover.chronoUnit()));
        };
    return format(nextBucketStart, dateFormat);
  }

  /**
   * Fails fast on an archiver configuration where the rollover date format is too coarse for the
   * rollover interval. Bucket trimming compares fetched endDate values (rendered with {@code
   * dateFormat}) against the next bucket start, so the format granularity must be at least as fine
   * as the interval. Otherwise, the bucket end collapses onto the start, the batch comes back
   * empty, and archiving silently stalls - see {@link #getNextBucketStart}.
   *
   * @throws IllegalArgumentException if {@code dateFormat} is coarser than {@code rolloverInterval}
   */
  public static void validateRolloverConfiguration(
      final String rolloverInterval, final String dateFormat) {
    // fail fast if the format cannot be produced and parsed back by this util (e.g. "yyyy-MM",
    // "yyyy"), which would otherwise blow up later in parseFlexibleDateTime during archiving
    try {
      parseFlexibleDateTime(format(VALIDATION_SAMPLE, dateFormat), dateFormat);
    } catch (final RuntimeException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid archiver configuration: rollover date format '%s' is not a valid.",
              dateFormat),
          e);
    }

    final ChronoUnit intervalUnit = parseTemporalAmount(rolloverInterval).chronoUnit();
    final ChronoUnit formatUnit = dateFormatChronoUnit(dateFormat);
    if (granularityOrdinal(formatUnit) > granularityOrdinal(intervalUnit)) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid archiver configuration: rollover date format '%s' is too coarse for rollover"
                  + " interval '%s'. The date format granularity must be at least as fine as the"
                  + " rollover interval (use a date-time format for sub-day rollover intervals).",
              dateFormat, rolloverInterval));
    }
  }

  private static LocalDateTime bucketStartDateTime(
      final String endDate, final String rolloverInterval, final String dateFormat) {
    final Temp rollover = parseTemporalAmount(rolloverInterval);
    final LocalDateTime archiveDate = parseFlexibleDateTime(endDate, dateFormat);
    final ZoneId utc = ZoneId.of("UTC");

    final LocalDateTime bucketStart;
    switch (rollover.chronoUnit()) {
      // NOTES:
      //  SECONDS is the smallest unit
      //  Floor integer division gives us the bucket that contains endDate
      case DAYS, HOURS, MINUTES, SECONDS -> {
        final long rolloverSeconds =
            Duration.of(rollover.amount(), rollover.chronoUnit()).getSeconds();
        final long secondsSinceEpoch = archiveDate.atZone(utc).toEpochSecond();
        final long bucketStartSeconds = (secondsSinceEpoch / rolloverSeconds) * rolloverSeconds;
        bucketStart = Instant.ofEpochSecond(bucketStartSeconds).atZone(utc).toLocalDateTime();
      }
      case WEEKS ->
          // ES calendar weeks are ISO-8601: they start on Monday (matches the prior
          // date_histogram.calendarInterval behaviour). Align to the Monday of endDate's week.
          bucketStart =
              archiveDate
                  .toLocalDate()
                  .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                  .atStartOfDay();
      case MONTHS -> {
        final int totalMonthsSinceEpoch =
            (archiveDate.getYear() - LocalDate.EPOCH.getYear()) * 12
                + (archiveDate.getMonthValue() - 1);
        final int bucketMonth = (totalMonthsSinceEpoch / rollover.amount()) * rollover.amount();
        final int year = LocalDate.EPOCH.getYear() + (bucketMonth / 12);
        final int month = (bucketMonth % 12) + 1;
        bucketStart = LocalDate.of(year, month, 1).atStartOfDay();
      }
      default ->
          throw new IllegalArgumentException("Unsupported rollover value: " + rolloverInterval);
    }
    return bucketStart;
  }

  private static String format(final LocalDateTime dateTime, final String dateFormat) {
    if (isDateOnly(dateFormat)) {
      return dateTime.format(DATE_ONLY_FORMATTER);
    } else {
      return dateTime.format(DateTimeFormatter.ofPattern(dateFormat));
    }
  }

  private static Temp parseTemporalAmount(final String input) {
    final Matcher matcher = TEMPORAL_PATTERN.matcher(input);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid format: " + input);
    }
    final int amount = Integer.parseInt(matcher.group(1));
    final String unit = matcher.group(2);
    return switch (unit) {
      case "s" -> new Temp(amount, ChronoUnit.SECONDS);
      case "m" -> new Temp(amount, ChronoUnit.MINUTES);
      case "h" -> new Temp(amount, ChronoUnit.HOURS);
      case "d" -> new Temp(amount, ChronoUnit.DAYS);
      case "w" -> new Temp(amount, ChronoUnit.WEEKS);
      case "M" -> new Temp(amount, ChronoUnit.MONTHS);
      default -> throw new IllegalArgumentException("Unsupported time amount: " + unit);
    };
  }

  private static LocalDateTime parseFlexibleDateTime(final String dateStr, final String pattern) {
    try {
      if (isDateOnly(pattern)) {
        return LocalDate.parse(dateStr, DATE_ONLY_FORMATTER).atStartOfDay();
      } else {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return LocalDateTime.parse(dateStr, formatter);
      }
    } catch (final Exception exception) {
      throw new IllegalArgumentException(
          "Invalid date format: " + dateStr + " with pattern: " + pattern, exception);
    }
  }

  private static boolean isDateOnly(final String pattern) {
    return "date".equals(pattern) || DATE_PATTERN.equals(pattern);
  }

  /** Finer units rank lower; used only to compare granularity, not for arithmetic. */
  private static int granularityOrdinal(final ChronoUnit unit) {
    return switch (unit) {
      case SECONDS -> 1;
      case MINUTES -> 2;
      case HOURS -> 3;
      case DAYS -> 4;
      case WEEKS -> 5;
      case MONTHS -> 6;
      default -> 7;
    };
  }

  /**
   * Finest temporal field a date format can render, ignoring quoted literals (e.g. {@code 'T'}).
   */
  private static ChronoUnit dateFormatChronoUnit(final String dateFormat) {
    if (isDateOnly(dateFormat)) {
      return ChronoUnit.DAYS;
    }
    final String unquoted = dateFormat.replaceAll("'[^']*'", "");
    if (unquoted.indexOf('s') >= 0) {
      return ChronoUnit.SECONDS;
    }
    if (unquoted.indexOf('m') >= 0) {
      return ChronoUnit.MINUTES;
    }
    if (unquoted.indexOf('H') >= 0
        || unquoted.indexOf('h') >= 0
        || unquoted.indexOf('K') >= 0
        || unquoted.indexOf('k') >= 0) {
      return ChronoUnit.HOURS;
    }
    if (unquoted.indexOf('d') >= 0) {
      return ChronoUnit.DAYS;
    }
    if (unquoted.indexOf('M') >= 0) {
      return ChronoUnit.MONTHS;
    }
    return ChronoUnit.YEARS;
  }

  private record Temp(int amount, ChronoUnit chronoUnit) {}
}
