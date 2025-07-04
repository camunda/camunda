/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DateUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(DateUtil.class);

  private DateUtil() {}

  public static OffsetDateTime toOffsetDateTime(final Instant timestamp) {
    return OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC);
  }

  public static OffsetDateTime toOffsetDateTime(final Long timestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
  }

  public static OffsetDateTime toOffsetDateTime(final String timestamp) {
    return toOffsetDateTime(timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }

  public static OffsetDateTime toOffsetDateTime(
      final String timestamp, final DateTimeFormatter dateTimeFormatter) {
    try {
      final ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestamp, dateTimeFormatter);
      return OffsetDateTime.ofInstant(zonedDateTime.toInstant(), ZoneOffset.UTC);
    } catch (final DateTimeParseException e) {
      LOGGER.error(String.format("Cannot parse date from %s - %s", timestamp, e.getMessage()), e);
    }

    return null;
  }

  public static OffsetDateTime fuzzyToOffsetDateTime(final Object object) {
    return switch (object) {
      case null -> null;
      case final OffsetDateTime offsetDateTime -> offsetDateTime;
      case final Instant instant -> toOffsetDateTime(instant);
      case final Long l -> toOffsetDateTime(Instant.ofEpochMilli(l));
      case final String s -> toOffsetDateTime(s);
      default ->
          throw new IllegalArgumentException(
              "Could not convert " + object.getClass() + " to OffsetDateTime");
    };
  }
}
