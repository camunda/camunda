/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.group;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;

import java.time.temporal.ChronoUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AggregateByDateUnitMapper {
  private static final String UNSUPPORTED_UNIT_STRING = "Unsupported unit: ";

  public static ChronoUnit mapToChronoUnit(final AggregateByDateUnit unit) {
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

  public static AggregateByDateUnit mapToAggregateByDateUnit(final ChronoUnit unit) {
    switch (unit) {
      case YEARS:
        return AggregateByDateUnit.YEAR;
      case MONTHS:
        return AggregateByDateUnit.MONTH;
      case WEEKS:
        return AggregateByDateUnit.WEEK;
      case DAYS:
        return AggregateByDateUnit.DAY;
      case HOURS:
        return AggregateByDateUnit.HOUR;
      case MINUTES:
        return AggregateByDateUnit.MINUTE;
      default:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }

  public static DateHistogramInterval mapToDateHistogramInterval(final AggregateByDateUnit unit) {
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
