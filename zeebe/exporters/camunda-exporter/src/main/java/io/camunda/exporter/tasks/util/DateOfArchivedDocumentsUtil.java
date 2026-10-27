/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

import io.camunda.search.schema.config.RetentionConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateOfArchivedDocumentsUtil {
  private static final Pattern TEMPORAL_PATTERN = Pattern.compile("(\\d+)" + "([smhdwMy])");
  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final DateTimeFormatter DATE_ONLY_FORMATTER =
      DateTimeFormatter.ofPattern(DATE_PATTERN);

  private static Temp parseTemporalAmount(final String input) throws IllegalArgumentException {
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
      case "w" -> new Temp(amount * 7, ChronoUnit.DAYS);
      case "M" -> new Temp(amount, ChronoUnit.MONTHS);
      default -> throw new IllegalArgumentException("Unsupported time amount: " + unit);
    };
  }

  public static Optional<Duration> getRetentionPolicyMinimumAge(
      final RetentionConfiguration retentionConfiguration, final String policyName) {
    final String minimumAge;
    if (policyName.equals(retentionConfiguration.getPolicyName())) {
      minimumAge = retentionConfiguration.getMinimumAge();
    } else if (policyName.equals(retentionConfiguration.getUsageMetricsPolicyName())) {
      minimumAge = retentionConfiguration.getUsageMetricsMinimumAge();
    } else {
      return Optional.empty();
    }
    final var temp = parseTemporalAmount(minimumAge);
    return Optional.of(Duration.of(temp.amount(), temp.chronoUnit()));
  }

  public static String getBucketStart(
      final String endDate, final String rolloverInterval, final String dateFormat) {
    final Temp rollover = parseTemporalAmount(rolloverInterval);
    final LocalDateTime archiveDate = parseFlexibleDateTime(endDate, dateFormat);
    final ZoneId utc = ZoneId.of("UTC");

    final LocalDateTime bucketStart;
    switch (rollover.chronoUnit()) {
      // NOTES:
      //  WEEKS is already handled by parseTemporalAmount as 7 days
      //  SECONDS is the smallest unit
      //  Floor integer division gives us the bucket that contains endDate
      case DAYS, HOURS, MINUTES, SECONDS -> {
        final long rolloverSeconds =
            Duration.of(rollover.amount(), rollover.chronoUnit()).getSeconds();
        final long secondsSinceEpoch = archiveDate.atZone(utc).toEpochSecond();
        final long bucketStartSeconds = (secondsSinceEpoch / rolloverSeconds) * rolloverSeconds;
        bucketStart = Instant.ofEpochSecond(bucketStartSeconds).atZone(utc).toLocalDateTime();
      }
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

    if (isDateOnly(dateFormat)) {
      return bucketStart.format(DATE_ONLY_FORMATTER);
    } else {
      return bucketStart.format(DateTimeFormatter.ofPattern(dateFormat));
    }
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

  private record Temp(int amount, ChronoUnit chronoUnit) {}
}
