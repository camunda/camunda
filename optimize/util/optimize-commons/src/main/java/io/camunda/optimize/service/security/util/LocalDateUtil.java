/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class LocalDateUtil {

  private static volatile OffsetDateTime CURRENT_TIME = null;

  private LocalDateUtil() {}

  public static void setCurrentTime(final OffsetDateTime currentTime) {
    LocalDateUtil.CURRENT_TIME = normalize(currentTime);
  }

  public static void reset() {
    LocalDateUtil.CURRENT_TIME = null;
  }

  public static OffsetDateTime getCurrentDateTime() {
    OffsetDateTime value = CURRENT_TIME;
    if (value == null) {
      value = normalize(OffsetDateTime.now());
    }
    return normalize(value);
  }

  public static OffsetDateTime getCurrentTimeWithTimezone(final ZoneId timezone) {
    return atSameTimezoneOffsetDateTime(getCurrentDateTime(), timezone);
  }

  public static LocalDateTime getCurrentLocalDateTime() {
    return getCurrentDateTime().toLocalDateTime();
  }

  private static OffsetDateTime normalize(final OffsetDateTime dateTime) {
    return dateTime.truncatedTo(ChronoUnit.MILLIS);
  }

  public static OffsetDateTime atSameTimezoneOffsetDateTime(
      final OffsetDateTime date, final ZoneId timezone) {
    if (date != null) {
      return date.atZoneSameInstant(timezone).toOffsetDateTime();
    }
    return null;
  }
}
