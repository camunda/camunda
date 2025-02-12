/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.apache.commons.lang3.math.NumberUtils;

public class OffsetDateTimeUtil {

  /**
   * Adds the given duration where it can either be simple a amount of days, or a ISO-8601 duration.
   *
   * @param dateTime ... The date time to add the duration to
   * @param duration ... Number of days or ISO-8601 duration
   * @return
   */
  public static OffsetDateTime addDuration(final OffsetDateTime dateTime, final String duration) {
    if (NumberUtils.isCreatable(duration)) {
      return dateTime.plusDays(NumberUtils.toInt(duration));
    }

    try {
      return dateTime.plus(Duration.parse(duration));
    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(
          "Invalid duration string (Must be ISO-8601 or just a number of days): " + duration, e);
    }
  }
}
