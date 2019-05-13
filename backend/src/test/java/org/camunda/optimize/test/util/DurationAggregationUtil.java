/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;

import java.util.Map;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;

public class DurationAggregationUtil {

  public static Long calculateExpectedValueGivenDurationsDefaultAggr(final Long... setDuration) {
    return calculateExpectedValueGivenDurations(setDuration).get(AVERAGE);
  }

  public static Map<AggregationType, Long> calculateExpectedValueGivenDurations(final Long... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Long::doubleValue).forEach(statistics::addValue);

    return ImmutableMap.of(MIN, Math.round(statistics.getMin()),
                           MAX, Math.round(statistics.getMax()),
                           AVERAGE, Math.round(statistics.getMean()),
                           MEDIAN, Math.round(statistics.getPercentile(50.0D))
    );
  }
}
