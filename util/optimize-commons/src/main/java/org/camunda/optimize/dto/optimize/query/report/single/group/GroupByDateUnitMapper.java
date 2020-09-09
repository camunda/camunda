/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.group;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

import java.time.temporal.ChronoUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupByDateUnitMapper {
  private static final String UNSUPPORTED_UNIT_STRING = "Unsupported unit: ";

  public static ChronoUnit mapToChronoUnit(final GroupByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return ChronoUnit.YEARS;
      case MONTH:
        return ChronoUnit.MONTHS;
      case WEEK:
        return ChronoUnit.WEEKS;
      case DAY:
        return ChronoUnit.DAYS;
      case HOUR:
        return ChronoUnit.HOURS;
      case MINUTE:
        return ChronoUnit.MINUTES;
      default:
      case AUTOMATIC:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }

  public static GroupByDateUnit mapToGroupByDateUnit(final ChronoUnit unit) {
    switch (unit) {
      case YEARS:
        return GroupByDateUnit.YEAR;
      case MONTHS:
        return GroupByDateUnit.MONTH;
      case WEEKS:
        return GroupByDateUnit.WEEK;
      case DAYS:
        return GroupByDateUnit.DAY;
      case HOURS:
        return GroupByDateUnit.HOUR;
      case MINUTES:
        return GroupByDateUnit.MINUTE;
      default:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }

  public static DateHistogramInterval mapToDateHistogramInterval(final GroupByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return DateHistogramInterval.YEAR;
      case MONTH:
        return DateHistogramInterval.MONTH;
      case WEEK:
        return DateHistogramInterval.WEEK;
      case DAY:
        return DateHistogramInterval.DAY;
      case HOUR:
        return DateHistogramInterval.HOUR;
      case MINUTE:
        return DateHistogramInterval.MINUTE;
      default:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }
}
