/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
    ZonedDateTime truncatedDate;
    if (unit.equals(ChronoUnit.HOURS)) {
      truncatedDate = date.atZoneSimilarLocal(ZoneId.systemDefault()).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.DAYS)) {
      truncatedDate = date.atZoneSimilarLocal(ZoneId.systemDefault()).truncatedTo(unit);
    } else if (unit.equals(ChronoUnit.WEEKS)) {
      truncatedDate = date.atZoneSimilarLocal(ZoneId.systemDefault()).with(DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
    } else if (unit.equals(ChronoUnit.MONTHS)) {
      truncatedDate = date.atZoneSimilarLocal(ZoneId.systemDefault()).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    } else {
      truncatedDate = date.atZoneSimilarLocal(ZoneId.systemDefault()).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
    }
    return truncatedDate;
  }
}
