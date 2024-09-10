/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

import io.camunda.optimize.service.db.report.MinMaxStatDto;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class DateAggregationService {
  public static Duration getDateHistogramIntervalDurationFromMinMax(
      final MinMaxStatDto minMaxStats) {
    final long intervalFromMinToMax =
        (long) (minMaxStats.getMax() - minMaxStats.getMin())
            / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
    // we need to ensure that the interval is > 1 since we create the range buckets based on this
    // interval and it will cause an endless loop if the interval is 0.
    return Duration.of(Math.max(intervalFromMinToMax, 1), ChronoUnit.MILLIS);
  }
}
