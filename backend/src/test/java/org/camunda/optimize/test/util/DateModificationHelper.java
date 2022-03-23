/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class DateModificationHelper {

  private DateModificationHelper() {
  }

  public static ZonedDateTime truncateToStartOfUnit(final OffsetDateTime date, final ChronoUnit unit) {
    return truncateToStartOfUnit(date, unit, ZoneId.systemDefault());
  }

  public static ZonedDateTime truncateToStartOfUnit(final OffsetDateTime date,
                                                    final ChronoUnit unit,
                                                    final ZoneId timezone) {
    ZonedDateTime truncatedDate;
    if (unit.equals(ChronoUnit.MINUTES)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.HOURS)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.DAYS)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.WEEKS)) {
      truncatedDate = date.atZoneSameInstant(timezone)
        .with(DayOfWeek.MONDAY)
        .truncatedTo(ChronoUnit.DAYS);
    } else if (unit.equals(ChronoUnit.MONTHS)) {
      truncatedDate = date.atZoneSameInstant(timezone).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    } else {
      truncatedDate = date.atZoneSameInstant(timezone).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    }
    return truncatedDate;
  }
}
