/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MEDIAN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;

public class DurationAggregationUtil {

  public static Double calculateExpectedValueGivenDurationsDefaultAggr(final Number setDuration) {
    return Optional.ofNullable(setDuration)
      .map(DurationAggregationUtil::calculateExpectedValueGivenDurations)
      .map(stats -> stats.get(AVERAGE))
      .orElse(null);
  }

  public static Double calculateExpectedValueGivenDurationsDefaultAggr(final Number... setDuration) {
    final double aggregatedDuration = calculateExpectedValueGivenDurations(setDuration).get(AVERAGE);
    // for duration we should omit the decimal numbers since it's not relevant for the user
    return Precision.round(aggregatedDuration, 0);
  }

  public static Map<AggregationType, Double> calculateExpectedValueGivenDurations(final Number... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Number::longValue).forEach(statistics::addValue);

    // for duration we should omit the decimal numbers since it's not relevant for the user
    return ImmutableMap.of(
      MIN, Precision.round(statistics.getMin(), 0),
      MAX, Precision.round(statistics.getMax(), 0),
      AVERAGE, Precision.round(statistics.getMean(), 0),
      MEDIAN, Precision.round(statistics.getPercentile(50.0D), 0)
    );
  }
}
