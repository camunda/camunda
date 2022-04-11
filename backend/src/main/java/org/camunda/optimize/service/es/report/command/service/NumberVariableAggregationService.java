/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.service;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.util.VariableAggregationContext;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.camunda.optimize.es.aggregations.NumberHistogramAggregationUtil.generateHistogramWithField;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLE_HISTOGRAM_AGGREGATION;
import static org.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static org.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

@RequiredArgsConstructor
@Component
public class NumberVariableAggregationService {
  public Optional<AggregationBuilder> createNumberVariableAggregation(final VariableAggregationContext context) {
    if (context.getVariableRangeMinMaxStats().isEmpty()) {
      return Optional.empty();
    }

    final Optional<Double> min = getBaselineForNumberVariableAggregation(context);
    if (min.isEmpty()) {
      // no valid baseline is set, return empty result
      return Optional.empty();
    }

    final double intervalSize = getIntervalSize(context, min.get());
    final double max = context.getMaxVariableValue();

    final String digitFormat = VariableType.DOUBLE.equals(context.getVariableType()) ? "0.00" : "0";

    final HistogramAggregationBuilder histogramAggregation = generateHistogramWithField(
      VARIABLE_HISTOGRAM_AGGREGATION,
      intervalSize,
      min.get(),
      max,
      context.getNestedVariableValueFieldLabel(),
      digitFormat,
      context.getSubAggregations()
    );

    return Optional.of(histogramAggregation);
  }

  private Double getIntervalSize(final VariableAggregationContext context,
                                 final Double baseline) {
    final double maxVariableValue = context.getMaxVariableValue();
    final boolean customBucketsActive = context.getCustomBucketDto().isActive();
    Double intervalSize = context.getCustomBucketDto().getBucketSize();
    if (!customBucketsActive || intervalSize == null || intervalSize <= 0) {
      // if no valid bucketSize is configured, calculate default automatic bucketSize
      intervalSize =
        Math.abs(maxVariableValue - baseline)
          / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1); // -1 because the end of the loop is
      // inclusive and would otherwise create 81 buckets
      intervalSize = intervalSize == 0 ? 1 : roundUpToNearestPowerOfTen(intervalSize);
    }
    if (!VariableType.DOUBLE.equals(context.getVariableType())) {
      // round bucketSize up if grouped by number variable without decimal point
      intervalSize = Math.ceil(intervalSize);
    }
    return intervalSize;
  }

  Optional<Double> getBaselineForNumberVariableAggregation(
    final VariableAggregationContext context) {
    final Optional<MinMaxStatDto> combinedMinMaxStats = context.getCombinedRangeMinMaxStats();
    final Optional<Double> baselineForSingleReport = context.getCustomBucketDto().isActive()
      ? Optional.ofNullable(context.getCustomBucketDto().getBaseline())
      : Optional.empty();

    if (combinedMinMaxStats.isEmpty() && baselineForSingleReport.isPresent()) {
      if (baselineForSingleReport.get() > context.getVariableRangeMinMaxStats().getMax()) {
        // if report is single report and invalid baseline is set, return empty result
        return Optional.empty();
      }
      // if report is single report and a valid baseline is set, use this instead of the min. range value
      return baselineForSingleReport;
    }

    return Optional.of(
      roundDownToNearestPowerOfTen(combinedMinMaxStats.orElse(context.getVariableRangeMinMaxStats()).getMin())
    );
  }
}
