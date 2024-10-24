/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class CustomBucketDto {

  private boolean active = false;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double bucketSize = 10.0;

  private BucketUnit bucketSizeUnit = null;

  // baseline = start of first bucket, if left null or customBuckets are inactive,
  // the bucket range will start at the min value
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Double baseline = 0.0;

  private BucketUnit baselineUnit = null;

  public CustomBucketDto(
      final boolean active,
      final Double bucketSize,
      final BucketUnit bucketSizeUnit,
      final Double baseline,
      final BucketUnit baselineUnit) {
    this.active = active;
    this.bucketSize = bucketSize;
    this.bucketSizeUnit = bucketSizeUnit;
    this.baseline = baseline;
    this.baselineUnit = baselineUnit;
  }

  protected CustomBucketDto() {}

  public Optional<Double> getBucketSizeInUnit(final BucketUnit requestedBucketUnit) {
    return convertValueToRequestedUnit(requestedBucketUnit, bucketSize, bucketSizeUnit);
  }

  public Optional<Double> getBaselineInUnit(final BucketUnit requestedBucketUnit) {
    return convertValueToRequestedUnit(requestedBucketUnit, baseline, baselineUnit);
  }

  private Optional<Double> convertValueToRequestedUnit(
      final BucketUnit requestedUnit, final Double currentValue, final BucketUnit currentUnit) {
    if (currentValue == null) {
      return Optional.empty();
    }

    if (requestedUnit == null || currentUnit == null || requestedUnit == currentUnit) {
      return Optional.of(currentValue);
    }
    return Optional.of(
        getBucketUnitRawValue(currentUnit) * currentValue / getBucketUnitRawValue(requestedUnit));
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

  public boolean isActive() {
    return active;
  }

  public void setActive(final boolean active) {
    this.active = active;
  }

  public Double getBucketSize() {
    return bucketSize;
  }

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  public void setBucketSize(final Double bucketSize) {
    this.bucketSize = bucketSize;
  }

  public BucketUnit getBucketSizeUnit() {
    return bucketSizeUnit;
  }

  public void setBucketSizeUnit(final BucketUnit bucketSizeUnit) {
    this.bucketSizeUnit = bucketSizeUnit;
  }

  public Double getBaseline() {
    return baseline;
  }

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  public void setBaseline(final Double baseline) {
    this.baseline = baseline;
  }

  public BucketUnit getBaselineUnit() {
    return baselineUnit;
  }

  public void setBaselineUnit(final BucketUnit baselineUnit) {
    this.baselineUnit = baselineUnit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CustomBucketDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "CustomBucketDto(active="
        + isActive()
        + ", bucketSize="
        + getBucketSize()
        + ", bucketSizeUnit="
        + getBucketSizeUnit()
        + ", baseline="
        + getBaseline()
        + ", baselineUnit="
        + getBaselineUnit()
        + ")";
  }

  private static boolean defaultActive() {
    return false;
  }

  private static Double defaultBucketSize() {
    return 10.0;
  }

  private static BucketUnit defaultBucketSizeUnit() {
    return null;
  }

  private static Double defaultBaseline() {
    return 0.0;
  }

  private static BucketUnit defaultBaselineUnit() {
    return null;
  }

  public static CustomBucketDtoBuilder builder() {
    return new CustomBucketDtoBuilder();
  }

  public static class CustomBucketDtoBuilder {

    private boolean activeValue;
    private boolean activeSet;
    private Double bucketSizeValue;
    private boolean bucketSizeSet;
    private BucketUnit bucketSizeUnitValue;
    private boolean bucketSizeUnitSet;
    private Double baselineValue;
    private boolean baselineSet;
    private BucketUnit baselineUnitValue;
    private boolean baselineUnitSet;

    CustomBucketDtoBuilder() {}

    public CustomBucketDtoBuilder active(final boolean active) {
      activeValue = active;
      activeSet = true;
      return this;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public CustomBucketDtoBuilder bucketSize(final Double bucketSize) {
      bucketSizeValue = bucketSize;
      bucketSizeSet = true;
      return this;
    }

    public CustomBucketDtoBuilder bucketSizeUnit(final BucketUnit bucketSizeUnit) {
      bucketSizeUnitValue = bucketSizeUnit;
      bucketSizeUnitSet = true;
      return this;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public CustomBucketDtoBuilder baseline(final Double baseline) {
      baselineValue = baseline;
      baselineSet = true;
      return this;
    }

    public CustomBucketDtoBuilder baselineUnit(final BucketUnit baselineUnit) {
      baselineUnitValue = baselineUnit;
      baselineUnitSet = true;
      return this;
    }

    public CustomBucketDto build() {
      boolean activeValue = this.activeValue;
      if (!activeSet) {
        activeValue = CustomBucketDto.defaultActive();
      }
      Double bucketSizeValue = this.bucketSizeValue;
      if (!bucketSizeSet) {
        bucketSizeValue = CustomBucketDto.defaultBucketSize();
      }
      BucketUnit bucketSizeUnitValue = this.bucketSizeUnitValue;
      if (!bucketSizeUnitSet) {
        bucketSizeUnitValue = CustomBucketDto.defaultBucketSizeUnit();
      }
      Double baselineValue = this.baselineValue;
      if (!baselineSet) {
        baselineValue = CustomBucketDto.defaultBaseline();
      }
      BucketUnit baselineUnitValue = this.baselineUnitValue;
      if (!baselineUnitSet) {
        baselineUnitValue = CustomBucketDto.defaultBaselineUnit();
      }
      return new CustomBucketDto(
          activeValue, bucketSizeValue, bucketSizeUnitValue, baselineValue, baselineUnitValue);
    }

    @Override
    public String toString() {
      return "CustomBucketDto.CustomBucketDtoBuilder(active$value="
          + activeValue
          + ", bucketSizeValue="
          + bucketSizeValue
          + ", bucketSizeUnitValue="
          + bucketSizeUnitValue
          + ", baselineValue="
          + baselineValue
          + ", baselineUnitValue="
          + baselineUnitValue
          + ")";
    }
  }

  public enum Fields {
    active,
    bucketSize,
    bucketSizeUnit,
    baseline,
    baselineUnit
  }
}
