/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.util;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import org.slf4j.Logger;

public final class TimeZoneUtil {

  private static final Set<String> AVAILABLE_ZONE_IDS = ZoneId.getAvailableZoneIds();
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TimeZoneUtil.class);

  private TimeZoneUtil() {}

  public static ZoneId extractTimezone(final HttpServletRequest request) {
    final String headerString = request.getHeader(X_OPTIMIZE_CLIENT_TIMEZONE);
    if (AVAILABLE_ZONE_IDS.contains(headerString)) {
      return ZoneId.of(headerString);
    } else if (headerString != null) {
      LOG.warn(
          "The provided timezone [{}] was not recognized. Falling back to server timezone [{}] instead.",
          headerString,
          ZoneId.systemDefault().getId());
    }
    // uses server timezone if unknown
    return ZoneId.systemDefault();
  }

  public static String formatToCorrectTimezone(
      final String dateAsString, final ZoneId timezone, final DateTimeFormatter dateTimeFormatter) {
    final OffsetDateTime date = OffsetDateTime.parse(dateAsString, dateTimeFormatter);
    final OffsetDateTime dateWithAdjustedTimezone =
        date.atZoneSameInstant(timezone).toOffsetDateTime();
    return dateTimeFormatter.format(dateWithAdjustedTimezone);
  }

  public static String formatToCorrectTimezone(
      final ZonedDateTime date, final ZoneId timezone, final DateTimeFormatter dateTimeFormatter) {
    return date.withZoneSameInstant(timezone).format(dateTimeFormatter);
  }
}
