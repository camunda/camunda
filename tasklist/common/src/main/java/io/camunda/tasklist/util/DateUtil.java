/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DateUtil {

  public static final SimpleDateFormat SIMPLE_DATE_FORMAT =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
  private static final Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);

  private static final Random RANDOM = new Random();

  public static OffsetDateTime getRandomStartDate() {
    Instant now = Instant.now();
    now = now.minus((5 + RANDOM.nextInt(10)), ChronoUnit.DAYS);
    now = now.minus(RANDOM.nextInt(60 * 24), ChronoUnit.MINUTES);
    final Clock clock = Clock.fixed(now, ZoneId.systemDefault());
    return OffsetDateTime.now(clock);
  }

  public static OffsetDateTime getRandomEndDate() {
    return getRandomEndDate(false);
  }

  public static OffsetDateTime getRandomEndDate(boolean nullable) {
    if (nullable) {
      if (RANDOM.nextInt(10) % 3 == 1) {
        return null;
      }
    }
    Instant now = Instant.now();
    now = now.minus((1 + RANDOM.nextInt(4)), ChronoUnit.DAYS);
    now = now.minus(RANDOM.nextInt(60 * 24), ChronoUnit.MINUTES);
    final Clock clock = Clock.fixed(now, ZoneId.systemDefault());
    return OffsetDateTime.now(clock);
  }

  public static OffsetDateTime toOffsetDateTime(Instant timestamp) {
    return OffsetDateTime.ofInstant(timestamp, ZoneId.systemDefault());
  }

  public static OffsetDateTime toOffsetDateTime(String timestamp) {
    return toOffsetDateTime(timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }

  public static OffsetDateTime toOffsetDateTime(String timestamp, String pattern) {
    return toOffsetDateTime(timestamp, DateTimeFormatter.ofPattern(pattern));
  }

  public static OffsetDateTime toOffsetDateTime(
      String timestamp, DateTimeFormatter dateTimeFormatter) {
    try {
      final ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestamp, dateTimeFormatter);
      return OffsetDateTime.ofInstant(zonedDateTime.toInstant(), ZoneId.systemDefault());
    } catch (DateTimeParseException e) {
      LOGGER.error(String.format("Cannot parse date from %s - %s", timestamp, e.getMessage()), e);
    }

    return null;
  }
}
