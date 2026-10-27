/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class DateModificationHelper {

  private DateModificationHelper() {}

  public static ZonedDateTime truncateToStartOfUnit(
      final OffsetDateTime date, final ChronoUnit unit) {
    return truncateToStartOfUnit(date, unit, ZoneId.systemDefault());
  }

  public static ZonedDateTime truncateToStartOfUnit(
      final OffsetDateTime date, final ChronoUnit unit, final ZoneId timezone) {
    final ZonedDateTime truncatedDate;
    if (unit.equals(ChronoUnit.MINUTES)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.HOURS)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.DAYS)) {
      truncatedDate = date.atZoneSameInstant(timezone).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.WEEKS)) {
      truncatedDate =
          date.atZoneSameInstant(timezone).with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
    } else if (unit.equals(ChronoUnit.MONTHS)) {
      truncatedDate =
          date.atZoneSameInstant(timezone).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    } else {
      truncatedDate =
          date.atZoneSameInstant(timezone).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    }
    return truncatedDate;
  }
}
