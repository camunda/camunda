/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;

public class DurationFormatterUtil {

  private DurationFormatterUtil() {}

  public static String formatMilliSecondsToReadableDurationString(long durationInMs) {
    if (durationInMs <= 0) {
      return "-";
    }

    final long years = TimeUnit.MILLISECONDS.toDays(durationInMs) / 365;
    durationInMs -= TimeUnit.DAYS.toMillis(years * 365);
    final long months = TimeUnit.MILLISECONDS.toDays(durationInMs) / 30;
    durationInMs -= TimeUnit.DAYS.toMillis(months * 30);
    final long weeks = TimeUnit.MILLISECONDS.toDays(durationInMs) / 7;
    durationInMs -= TimeUnit.DAYS.toMillis(weeks * 7);
    final long days = TimeUnit.MILLISECONDS.toDays(durationInMs);
    durationInMs -= TimeUnit.DAYS.toMillis(days);
    final long hours = TimeUnit.MILLISECONDS.toHours(durationInMs);
    durationInMs -= TimeUnit.HOURS.toMillis(hours);
    final long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMs);
    durationInMs -= TimeUnit.MINUTES.toMillis(minutes);
    final long seconds = TimeUnit.MILLISECONDS.toSeconds(durationInMs);
    durationInMs -= TimeUnit.SECONDS.toMillis(seconds);

    final StringBuilder sb = new StringBuilder();
    if (years > 0) {
      sb.append(String.format("%dyrs", years));
    }
    if (months > 0) {
      sb.append(String.format(" %dmo", months));
    }
    if (weeks > 0) {
      sb.append(String.format(" %dwks", weeks));
    }
    if (days > 0) {
      sb.append(String.format(" %dd", days));
    }
    if (hours > 0) {
      sb.append(String.format(" %dh", hours));
    }
    if (minutes > 0) {
      sb.append(String.format(" %dmin", minutes));
    }
    if (seconds > 0) {
      sb.append(String.format(" %ds", seconds));
    }
    if (durationInMs > 0) {
      sb.append(String.format(" %dms", durationInMs));
    }

    return StringUtils.strip(sb.toString());
  }

  public static String formatDuration(final String unitString, final long duration) {
    return duration <= 0 ? "" : String.format("%d%s", duration, unitString);
  }
}
