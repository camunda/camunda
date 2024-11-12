/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.service;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static io.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static io.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.context.VariableAggregationContext;
import java.util.Optional;

public class AbstractNumberVariableAggregationService {
  protected Double getIntervalSize(
      final VariableAggregationContext context, final Double baseline) {
    final double maxVariableValue = context.getMaxVariableValue();
    final boolean customBucketsActive = context.getCustomBucketDto().isActive();
    Double intervalSize = context.getCustomBucketDto().getBucketSize();
    if (!customBucketsActive || intervalSize == null || intervalSize <= 0) {
      // if no valid bucketSize is configured, calculate default automatic bucketSize
      intervalSize =
          Math.abs(maxVariableValue - baseline)
              / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION
                  - 1); // -1 because the end of the loop is inclusive
      intervalSize = intervalSize == 0 ? 1 : roundUpToNearestPowerOfTen(intervalSize);
    }
    if (!VariableType.DOUBLE.equals(context.getVariableType())) {
      // round bucketSize up if grouped by number variable without decimal point
      intervalSize = Math.ceil(intervalSize);
    }
    return intervalSize;
  }

  public Optional<Double> getBaselineForNumberVariableAggregation(
      final VariableAggregationContext context) {
    final Optional<MinMaxStatDto> combinedMinMaxStats = context.getCombinedRangeMinMaxStats();
    final Optional<Double> baselineForSingleReport =
        context.getCustomBucketDto().isActive()
            ? Optional.ofNullable(context.getCustomBucketDto().getBaseline())
            : Optional.empty();

    if (combinedMinMaxStats.isEmpty() && baselineForSingleReport.isPresent()) {
      if (baselineForSingleReport.get() > context.getVariableRangeMinMaxStats().getMax()) {
        // if report is single report and invalid baseline is set, return empty result
        return Optional.empty();
      }
      // if report is single report and a valid baseline is set, use this instead of the min. range
      // value
      return baselineForSingleReport;
    }

    return Optional.of(
        roundDownToNearestPowerOfTen(
            combinedMinMaxStats.orElse(context.getVariableRangeMinMaxStats()).getMin()));
  }
}
