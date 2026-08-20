/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static io.camunda.optimize.service.db.report.interpreter.util.AggregateByDateUnitMapper.mapToChronoUnit;
import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.previousOrSame;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class DateAggregationService {
  public static Duration getDateHistogramIntervalDurationFromMinMax(
      final MinMaxStatDto minMaxStats) {
    final long intervalFromMinToMax =
        (long) (minMaxStats.getMax() - minMaxStats.getMin())
            / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }

  protected ZonedDateTime getEndOfBucket(
      final ZonedDateTime startOfBucket,
      final AggregateByDateUnit unit,
      final Duration durationOfAutomaticInterval) {
    return AggregateByDateUnit.AUTOMATIC.equals(unit)
        ? startOfBucket.plus(durationOfAutomaticInterval)
        : startOfBucket.plus(1, mapToChronoUnit(unit));
  }

  protected ZonedDateTime truncateToUnit(
      final ZonedDateTime dateToTruncate, final AggregateByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return dateToTruncate.with(firstDayOfYear()).truncatedTo(ChronoUnit.DAYS);
      case MONTH:
        return dateToTruncate.with(firstDayOfMonth()).truncatedTo(ChronoUnit.DAYS);
      case WEEK:
        return dateToTruncate.with(previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
      case DAY:
      case HOUR:
      case MINUTE:
        return dateToTruncate.truncatedTo(mapToChronoUnit(unit));
      case AUTOMATIC:
        return dateToTruncate;
      default:
        throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
  }
}
