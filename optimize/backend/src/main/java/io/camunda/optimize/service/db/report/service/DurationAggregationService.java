/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.service;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static io.camunda.optimize.service.util.RoundingUtil.roundDownToNearestPowerOfTen;
import static io.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;

public class DurationAggregationService {
  protected static final String DURATION_HISTOGRAM_AGGREGATION = "durationHistogram";
  protected static final int AUTOMATIC_BUCKET_LIMIT =
      NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
  protected static final BucketUnit DEFAULT_UNIT = BucketUnit.MILLISECOND;
  protected static final DurationUnit FILTER_UNIT = DurationUnit.MILLIS;

  protected double getIntervalInMillis(
      final double minValueInMillis,
      final double maxValueInMillis,
      final CustomBucketDto customBucketDto) {
    final double distance = maxValueInMillis - minValueInMillis;
    final double interval;
    if (customBucketDto.isActive()) {
      interval = customBucketDto.getBucketSizeInUnit(DEFAULT_UNIT).orElse(1.0D);
    } else if (distance <= AUTOMATIC_BUCKET_LIMIT) {
      interval = 1.0D;
    } else {
      // this is the minimal interval needed to ensure there are no more buckets than the limit
      final int minimalInterval = (int) Math.ceil(distance / AUTOMATIC_BUCKET_LIMIT);
      // as base 10 intervals are easier to read
      interval = roundUpToNearestPowerOfTen((double) minimalInterval).intValue();
    }
    return interval;
  }

  protected double getMinValueInMillis(
      final MinMaxStatDto minMaxStats, final CustomBucketDto customBucketDto) {
    if (customBucketDto.isActive()) {
      return customBucketDto.getBaselineInUnit(DEFAULT_UNIT).orElse(0.0D);
    } else {
      return roundDownToNearestPowerOfTen(minMaxStats.getMin());
    }
  }

  protected String[] getIndexNames(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return getProcessInstanceIndexAliasNames(context.getReportData());
  }
}
