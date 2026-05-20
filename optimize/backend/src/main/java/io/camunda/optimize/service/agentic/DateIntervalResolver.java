/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.agentic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.NullMarked;

/** Resolves a calendar_interval string for date_histogram aggregations based on the date range. */
@NullMarked
public final class DateIntervalResolver {

  private DateIntervalResolver() {}

  public static String resolve(final Instant from, final Instant to) {
    final long days = ChronoUnit.DAYS.between(from, to);
    if (days <= 2) {
      return "1h";
    }
    if (days <= 30) {
      return "1d";
    }
    if (days <= 180) {
      return "1w";
    }
    return "1M";
  }
}
