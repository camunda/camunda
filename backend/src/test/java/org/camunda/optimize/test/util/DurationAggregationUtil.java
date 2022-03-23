/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tdunning.math.stats.TDigest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.AVERAGE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MAX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.MIN;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.PERCENTILE;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.SUM;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType.values;

public class DurationAggregationUtil {

  public static Double calculateExpectedValueGivenDurationsDefaultAggr(final Number setDuration) {
    return Optional.ofNullable(setDuration)
      .map(DurationAggregationUtil::calculateExpectedValueGivenDurations)
      .map(stats -> stats.get(new AggregationDto(AVERAGE)))
      .orElse(null);
  }

  public static Double calculateExpectedValueGivenDurationsDefaultAggr(final Number... setDuration) {
    final double aggregatedDuration = calculateExpectedValueGivenDurations(setDuration)
      .get(new AggregationDto(AVERAGE));
    // for duration, we should omit the decimal numbers since it's not relevant for the user
    return Precision.round(aggregatedDuration, 0);
  }

  public static AggregationDto[] getSupportedAggregationTypes() {
    List<AggregationDto> aggregationDtos = Arrays.stream(values())
      .filter(aggType -> !aggType.equals(PERCENTILE))
      .map(AggregationDto::new)
      .collect(Collectors.toList());
    aggregationDtos.addAll(List.of(
      new AggregationDto(PERCENTILE, 99.),
      new AggregationDto(PERCENTILE, 95.),
      new AggregationDto(PERCENTILE, 75.),
      new AggregationDto(PERCENTILE, 50.),
      new AggregationDto(PERCENTILE, 25.)
    ));
    return aggregationDtos.toArray(AggregationDto[]::new);
  }

  public static AggregationDto[] getAggregationTypesAsListForProcessParts() {
    // process parts does not support percentile since it does the result calculation
    // with a script and the script does not allow sorting over all values.
    return Arrays.stream(values())
      .filter(type -> !ImmutableSet.of(PERCENTILE).contains(type))
      .map(AggregationDto::new)
      .toArray(AggregationDto[]::new);
  }

  public static Map<AggregationDto, Double> calculateExpectedValueGivenDurations(final Number... setDuration) {
    final DescriptiveStatistics statistics = new DescriptiveStatistics();
    Stream.of(setDuration).map(Number::longValue).forEach(statistics::addValue);
    // Elasticsearch uses the TDigest algorithm internally for percentile calculations, so we must use the same
    // here when calculating the expected results
    final TDigest tDigest = TDigest.createAvlTreeDigest(100);
    Arrays.stream(setDuration).forEach(duration -> tDigest.add((double) duration));

    // for duration, we should omit the decimal numbers since it's not relevant for the user
    return ImmutableMap.of(
      new AggregationDto(MIN), Precision.round(statistics.getMin(), 0),
      new AggregationDto(MAX), Precision.round(statistics.getMax(), 0),
      new AggregationDto(AVERAGE), Precision.round(statistics.getMean(), 0),
      new AggregationDto(SUM), Precision.round(statistics.getSum(), 0),
      new AggregationDto(PERCENTILE, 99.), Precision.round(tDigest.quantile(0.99), 0),
      new AggregationDto(PERCENTILE, 95.), Precision.round(tDigest.quantile(0.95), 0),
      new AggregationDto(PERCENTILE, 75.), Precision.round(tDigest.quantile(0.75), 0),
      new AggregationDto(PERCENTILE, 50.), Precision.round(tDigest.quantile(0.50), 0),
      new AggregationDto(PERCENTILE, 25.), Precision.round(tDigest.quantile(0.25), 0)
    );
  }
}
