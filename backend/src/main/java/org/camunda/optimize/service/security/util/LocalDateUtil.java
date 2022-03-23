/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LocalDateUtil {

  private volatile static OffsetDateTime CURRENT_TIME = null;

  public static void setCurrentTime(OffsetDateTime currentTime) {
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

  public static OffsetDateTime atSameTimezoneOffsetDateTime(final OffsetDateTime date, final ZoneId timezone) {
    if (date != null) {
      return date.atZoneSameInstant(timezone).toOffsetDateTime();
    }
    return null;
  }

}
