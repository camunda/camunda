/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit.QUARTERS;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DateFilterUtil {

  public static OffsetDateTime getStartOfCurrentInterval(OffsetDateTime now, DateUnit dateUnit) {
    switch (dateUnit) {
      case MINUTES:
        return now
          .with(ChronoField.SECOND_OF_MINUTE, 0)
          .with(ChronoField.MILLI_OF_SECOND, 0)
          .with(ChronoField.NANO_OF_SECOND, 0);
      case HOURS:
        return now
          .with(ChronoField.MINUTE_OF_HOUR, 0)
          .with(ChronoField.SECOND_OF_MINUTE, 0)
          .with(ChronoField.MILLI_OF_SECOND, 0)
          .with(ChronoField.NANO_OF_SECOND, 0);
      case DAYS:
        return now
          .with(LocalTime.MIN);
      case WEEKS:
        return now
          .with(ChronoField.DAY_OF_WEEK, 1)
          .with(LocalTime.MIN);
      case MONTHS:
        return now
          .with(ChronoField.DAY_OF_MONTH, 1)
          .with(LocalTime.MIN);
      case QUARTERS:
        int completedMonthsInQuarter = (now.getMonthValue() + 2) % 3;
        return now
          .with(ChronoField.DAY_OF_MONTH, 1)
          .with(LocalTime.MIN)
          .minusMonths(completedMonthsInQuarter);
      case YEARS:
        return now
          .with(ChronoField.DAY_OF_YEAR, 1)
          .with(LocalTime.MIN);
      default:
        throw new OptimizeValidationException("Unknown date unit: " + dateUnit);
    }
  }

  public static OffsetDateTime getStartOfPreviousInterval(OffsetDateTime startOfCurrentInterval,
                                                          DateUnit dateUnit,
                                                          Long unitQuantity) {
    if (dateUnit.equals(QUARTERS)) {
      return startOfCurrentInterval.minus(3 * unitQuantity, ChronoUnit.MONTHS);
    } else {
      return startOfCurrentInterval.minus(unitQuantity, unitOf(dateUnit.getId()));
    }
  }

  public static TemporalUnit unitOf(String unit) {
    return ChronoUnit.valueOf(unit.toUpperCase());
  }

}
