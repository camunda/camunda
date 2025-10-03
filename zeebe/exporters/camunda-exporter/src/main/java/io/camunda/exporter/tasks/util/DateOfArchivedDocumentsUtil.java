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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DateOfArchivedDocumentsUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(DateOfArchivedDocumentsUtil.class);
  private static final Pattern TEMPORAL_PATTERN = Pattern.compile("(\\d+)" + "([smhdwMy])");
  private static final String DATE_PATTERN = "yyyy-MM-dd";
  private static final String DATE_AND_HOUR_PATTERN = "yyyy-MM-dd-HH";

  public static String calculateDateOfArchiveIndexForBatch(
      final String dateOfArchiveBatch,
      String dateOfMostRecentArchiveIndex,
      final String rolloverInterval,
      final String elsRolloverDateFormat) {

    // if historicalDate is not set, or rolloverInterval is not valid,
    // we default to the endDate.
    if (dateOfMostRecentArchiveIndex == null
        || dateOfMostRecentArchiveIndex.isEmpty()
        || rolloverInterval == null
        || rolloverInterval.isEmpty()) {
      return dateOfArchiveBatch;
    }

    final LocalDateTime endDateTime =
        parseFlexibleDateTime(dateOfArchiveBatch, elsRolloverDateFormat);
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
        dateOfMostRecentArchiveIndex = dateOfArchiveBatch;
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

  public static CompletableFuture<String> getLastHistoricalArchiverDate(
      final CompletableFuture<List<String>> listOfIndexes) {
    final DateTimeFormatter formatterWithHour = DateTimeFormatter.ofPattern(DATE_AND_HOUR_PATTERN);
    final DateTimeFormatter formatterWithoutHour = DateTimeFormatter.ofPattern(DATE_PATTERN);
    final Pattern indexDatePattern = Pattern.compile("_(\\d{4}-\\d{2}-\\d{2}(?:-\\d{2})?)");

    return listOfIndexes.thenApply(
        indexes ->
            indexes.stream()
                .map(
                    index -> {
                      final Matcher matcher = indexDatePattern.matcher(index);
                      if (matcher.find()) {
                        final String dateStr = matcher.group(1);
                        final LocalDateTime dateTime;
                        if (dateStr.length() == 13) { // e.g., 2025-06-16-10
                          dateTime = LocalDateTime.parse(dateStr, formatterWithHour);
                        } else { // e.g., 2025-06-16
                          dateTime = LocalDate.parse(dateStr, formatterWithoutHour).atStartOfDay();
                        }
                        return new AbstractMap.SimpleEntry<>(dateTime, dateStr);
                      }
                      return null;
                    })
                .filter(Objects::nonNull)
                .max(Comparator.comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .orElse(null));
  }

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

  private static LocalDateTime parseFlexibleDateTime(final String dateStr, final String pattern) {
    try {
      if ("date".equals(pattern) || DATE_PATTERN.equals(pattern)) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
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
