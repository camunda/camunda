/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit.QUARTERS;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

public class DateFilterUtil {

  private DateFilterUtil() {}

  public static OffsetDateTime getStartOfCurrentInterval(
      final OffsetDateTime now, final DateUnit dateUnit) {
    switch (dateUnit) {
      case MINUTES:
        return now.with(ChronoField.SECOND_OF_MINUTE, 0)
            .with(ChronoField.MILLI_OF_SECOND, 0)
            .with(ChronoField.NANO_OF_SECOND, 0);
      case HOURS:
        return now.with(ChronoField.MINUTE_OF_HOUR, 0)
            .with(ChronoField.SECOND_OF_MINUTE, 0)
            .with(ChronoField.MILLI_OF_SECOND, 0)
            .with(ChronoField.NANO_OF_SECOND, 0);
      case DAYS:
        return now.with(LocalTime.MIN);
      case WEEKS:
        return now.with(ChronoField.DAY_OF_WEEK, 1).with(LocalTime.MIN);
      case MONTHS:
        return now.with(ChronoField.DAY_OF_MONTH, 1).with(LocalTime.MIN);
      case QUARTERS:
        final int completedMonthsInQuarter = (now.getMonthValue() + 2) % 3;
        return now.with(ChronoField.DAY_OF_MONTH, 1)
            .with(LocalTime.MIN)
            .minusMonths(completedMonthsInQuarter);
      case YEARS:
        return now.with(ChronoField.DAY_OF_YEAR, 1).with(LocalTime.MIN);
      default:
        throw new OptimizeValidationException("Unknown date unit: " + dateUnit);
    }
  }

  public static OffsetDateTime getStartOfPreviousInterval(
      final OffsetDateTime startOfCurrentInterval,
      final DateUnit dateUnit,
      final Long unitQuantity) {
    if (dateUnit.equals(QUARTERS)) {
      return startOfCurrentInterval.minus(3 * unitQuantity, ChronoUnit.MONTHS);
    } else {
      return startOfCurrentInterval.minus(unitQuantity, unitOf(dateUnit.getId()));
    }
  }

  public static TemporalUnit unitOf(final String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }
}
