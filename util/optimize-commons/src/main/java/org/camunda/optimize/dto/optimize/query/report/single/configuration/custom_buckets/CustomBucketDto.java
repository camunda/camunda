/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Data
@Builder
@FieldNameConstants(asEnum = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CustomBucketDto {
  @Builder.Default
  private boolean active = false;

  @Builder.Default
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double bucketSize = 10.0;
  @Builder.Default
  private BucketUnit bucketSizeUnit = null;

  // baseline = start of first bucket, if left null or customBuckets are inactive,
  // the bucket range will start at the min value
  @Builder.Default
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double baseline = 0.0;
  @Builder.Default
  private BucketUnit baselineUnit = null;

  public Optional<Double> getBucketSizeInUnit(final BucketUnit requestedBucketUnit) {
    return convertValueToRequestedUnit(requestedBucketUnit, this.bucketSize, this.bucketSizeUnit);
  }

  public Optional<Double> getBaselineInUnit(final BucketUnit requestedBucketUnit) {
    return convertValueToRequestedUnit(requestedBucketUnit, this.baseline, this.baselineUnit);
  }

  private Optional<Double> convertValueToRequestedUnit(final BucketUnit requestedUnit,
                                                       final Double currentValue,
                                                       final BucketUnit currentUnit) {
    if (currentValue == null) {
      return Optional.empty();
    }

    if (requestedUnit == null || currentUnit== null || requestedUnit == currentUnit) {
      return Optional.of(currentValue);
    }
    return Optional.of(getBucketUnitRawValue(currentUnit) * currentValue / getBucketUnitRawValue(requestedUnit));
  }

  private double getBucketUnitRawValue(final BucketUnit bucketUnit) {
    final ChronoUnit chronoUnit;
    switch (bucketUnit) {
      case YEAR:
        chronoUnit = ChronoUnit.YEARS;
        break;
      case MONTH:
        chronoUnit = ChronoUnit.MONTHS;
        break;
      case WEEK:
        chronoUnit = ChronoUnit.WEEKS;
        break;
      case DAY:
        chronoUnit = ChronoUnit.DAYS;
        break;
      case HOUR:
        chronoUnit = ChronoUnit.HOURS;
        break;
      case MINUTE:
        chronoUnit = ChronoUnit.MINUTES;
        break;
      case SECOND:
        chronoUnit = ChronoUnit.SECONDS;
        break;
      case MILLISECOND:
        chronoUnit = ChronoUnit.MILLIS;
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported bucket unit: " + bucketUnit);
    }
    return chronoUnit.getDuration().toMillis();
  }
}
