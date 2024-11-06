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

public final class DateUtil {

  private DateUtil() {}

  public static OffsetDateTime toOffsetDateTime(final Instant timestamp) {
    return OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC);
  }

  public static OffsetDateTime fuzzyToOffsetDateTime(final Object object) {
    return switch (object) {
      case null -> null;
      case final OffsetDateTime offsetDateTime -> offsetDateTime;
      case final Instant instant -> toOffsetDateTime(instant);
      case final Long l -> toOffsetDateTime(Instant.ofEpochMilli(l));
      case final String s -> OffsetDateTime.parse(s);
      default ->
          throw new IllegalArgumentException(
              "Could not convert " + object.getClass() + " to OffsetDateTime");
    };
  }
}
