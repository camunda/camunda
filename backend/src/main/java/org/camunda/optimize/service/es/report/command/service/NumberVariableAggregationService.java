/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.range.RangeAggregator;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.modules.group_by.AbstractGroupByVariable.RANGE_AGGREGATION;
import static org.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

@RequiredArgsConstructor
@Component
public class NumberVariableAggregationService {

  private final ConfigurationService configurationService;

  public Optional<AggregationBuilder> createNumberVariableAggregation(
    final ExecutionContext<? extends SingleReportDataDto> context,
    final VariableType variableType,
    final String nestedVariableValueFieldLabel,
    final MinMaxStatDto minMaxStats,
    final CustomBucketDto customBucketDto) {
    if (minMaxStats.isEmpty()) {
      return Optional.empty();
    }

    final Optional<Double> min = getBaselineForNumberVariableAggregation(context, minMaxStats, customBucketDto);
    if (!min.isPresent()) {
      // no valid baseline is set, return empty result
      return Optional.empty();
    }

    final double unit =
      getGroupByNumberVariableUnit(
        context,
        min.get(),
        minMaxStats,
        variableType,
        customBucketDto
      );
    final double max = getMaxForNumberVariableAggregation(context, minMaxStats);
    int numberOfBuckets = 0;

    RangeAggregationBuilder rangeAgg = AggregationBuilders
      .range(RANGE_AGGREGATION)
      .field(nestedVariableValueFieldLabel);

    for (double start = min.get();
         start <= max && numberOfBuckets < configurationService.getEsAggregationBucketLimit();
         start += unit, numberOfBuckets++) {
      RangeAggregator.Range range =
        new RangeAggregator.Range(
          getKeyForNumberBucket(variableType, start),
          start,
          start + unit
        );
      rangeAgg.addRange(range);
    }
    return Optional.of(rangeAgg);
  }

  public Double getGroupByNumberVariableUnit(final ExecutionContext<? extends SingleReportDataDto> context,
                                             final Double baseline,
                                             final MinMaxStatDto minMaxStats,
                                             final VariableType variableType,
                                             final CustomBucketDto customBucketDto) {
    final double maxVariableValue = context.getCombinedRangeMinMaxStats().orElse(minMaxStats).getMax();
    final boolean customBucketsActive = customBucketDto.isActive();
    Double unit = customBucketDto.getBucketSize();
    if (!customBucketsActive || unit == null || unit <= 0) {
      // if no valid unit is configured, calculate default automatic unit
      unit =
        (maxVariableValue - baseline)
          / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1); // -1 because the end of the loop is
      // inclusive and would otherwise create 81 buckets
      unit = unit == 0 ? 1 : roundDownToNearestPowerOfTen(unit);
    }
    if (!VariableType.DOUBLE.equals(variableType)) {
      // round unit up if grouped by number variable without decimal point
      unit = Math.ceil(unit);
    }
    return unit;
  }

  public Optional<Double> getBaselineForNumberVariableAggregation(
    final ExecutionContext<? extends SingleReportDataDto> context,
    final MinMaxStatDto minMaxStats,
    final CustomBucketDto customBucketDto) {
    final Optional<MinMaxStatDto> contextMinMaxStats = context.getCombinedRangeMinMaxStats();
    final Optional<Double> baselineForSingleReport = customBucketDto.isActive()
      ? Optional.ofNullable(customBucketDto.getBaseline())
      : Optional.empty();

    if (!contextMinMaxStats.isPresent() && baselineForSingleReport.isPresent()) {
      if (baselineForSingleReport.get() > minMaxStats.getMax()) {
        // if report is single report and invalid baseline is set, return empty result
        return Optional.empty();
      }
      // if report is single report and a valid baseline is set, use this instead of the min. range value
      return baselineForSingleReport;
    }

    return Optional.of(roundDownToNearestPowerOfTen(contextMinMaxStats.orElse(minMaxStats).getMin()));
  }

  private Double getMaxForNumberVariableAggregation(final ExecutionContext<? extends SingleReportDataDto> context,
                                                    final MinMaxStatDto minMaxStats) {
    return context.getCombinedRangeMinMaxStats().map(MinMaxStatDto::getMax).orElse(minMaxStats.getMax());
  }

  private String getKeyForNumberBucket(final VariableType varType,
                                       final double bucketStart) {
    if (!VariableType.DOUBLE.equals(varType)) {
      // truncate decimal point for non-double variable aggregations
      return String.valueOf((long) bucketStart);
    }
    DecimalFormatSymbols decimalSymbols = new DecimalFormatSymbols(Locale.US);
    final DecimalFormat decimalFormat = new DecimalFormat("0.00", decimalSymbols);
    return decimalFormat.format(bucketStart);
  }

}
