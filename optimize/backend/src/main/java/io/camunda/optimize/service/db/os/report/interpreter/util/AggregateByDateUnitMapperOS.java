/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.opensearch.client.opensearch._types.aggregations.CalendarInterval;

public final class AggregateByDateUnitMapperOS {

  private static final String UNSUPPORTED_UNIT_STRING = "Unsupported unit: ";

  private AggregateByDateUnitMapperOS() {}

  public static CalendarInterval mapToCalendarInterval(final AggregateByDateUnit unit) {
    switch (unit) {
      case YEAR:
        return CalendarInterval.Year;
      case MONTH:
        return CalendarInterval.Month;
      case WEEK:
        return CalendarInterval.Week;
      case DAY:
        return CalendarInterval.Day;
      case HOUR:
        return CalendarInterval.Hour;
      case MINUTE:
        return CalendarInterval.Minute;
      default:
        throw new IllegalArgumentException(UNSUPPORTED_UNIT_STRING + unit);
    }
  }
}
