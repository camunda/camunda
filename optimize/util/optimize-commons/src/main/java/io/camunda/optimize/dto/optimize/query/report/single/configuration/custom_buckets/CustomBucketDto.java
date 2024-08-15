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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isActive() ? 79 : 97);
    final Object $bucketSize = getBucketSize();
    result = result * PRIME + ($bucketSize == null ? 43 : $bucketSize.hashCode());
    final Object $bucketSizeUnit = getBucketSizeUnit();
    result = result * PRIME + ($bucketSizeUnit == null ? 43 : $bucketSizeUnit.hashCode());
    final Object $baseline = getBaseline();
    result = result * PRIME + ($baseline == null ? 43 : $baseline.hashCode());
    final Object $baselineUnit = getBaselineUnit();
    result = result * PRIME + ($baselineUnit == null ? 43 : $baselineUnit.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CustomBucketDto)) {
      return false;
    }
    final CustomBucketDto other = (CustomBucketDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isActive() != other.isActive()) {
      return false;
    }
    final Object this$bucketSize = getBucketSize();
    final Object other$bucketSize = other.getBucketSize();
    if (this$bucketSize == null
        ? other$bucketSize != null
        : !this$bucketSize.equals(other$bucketSize)) {
      return false;
    }
    final Object this$bucketSizeUnit = getBucketSizeUnit();
    final Object other$bucketSizeUnit = other.getBucketSizeUnit();
    if (this$bucketSizeUnit == null
        ? other$bucketSizeUnit != null
        : !this$bucketSizeUnit.equals(other$bucketSizeUnit)) {
      return false;
    }
    final Object this$baseline = getBaseline();
    final Object other$baseline = other.getBaseline();
    if (this$baseline == null ? other$baseline != null : !this$baseline.equals(other$baseline)) {
      return false;
    }
    final Object this$baselineUnit = getBaselineUnit();
    final Object other$baselineUnit = other.getBaselineUnit();
    if (this$baselineUnit == null
        ? other$baselineUnit != null
        : !this$baselineUnit.equals(other$baselineUnit)) {
      return false;
    }
    return true;
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

  private static boolean $default$active() {
    return false;
  }

  private static Double $default$bucketSize() {
    return 10.0;
  }

  private static BucketUnit $default$bucketSizeUnit() {
    return null;
  }

  private static Double $default$baseline() {
    return 0.0;
  }

  private static BucketUnit $default$baselineUnit() {
    return null;
  }

  public static CustomBucketDtoBuilder builder() {
    return new CustomBucketDtoBuilder();
  }

  public static class CustomBucketDtoBuilder {

    private boolean active$value;
    private boolean active$set;
    private Double bucketSize$value;
    private boolean bucketSize$set;
    private BucketUnit bucketSizeUnit$value;
    private boolean bucketSizeUnit$set;
    private Double baseline$value;
    private boolean baseline$set;
    private BucketUnit baselineUnit$value;
    private boolean baselineUnit$set;

    CustomBucketDtoBuilder() {}

    public CustomBucketDtoBuilder active(final boolean active) {
      active$value = active;
      active$set = true;
      return this;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public CustomBucketDtoBuilder bucketSize(final Double bucketSize) {
      bucketSize$value = bucketSize;
      bucketSize$set = true;
      return this;
    }

    public CustomBucketDtoBuilder bucketSizeUnit(final BucketUnit bucketSizeUnit) {
      bucketSizeUnit$value = bucketSizeUnit;
      bucketSizeUnit$set = true;
      return this;
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public CustomBucketDtoBuilder baseline(final Double baseline) {
      baseline$value = baseline;
      baseline$set = true;
      return this;
    }

    public CustomBucketDtoBuilder baselineUnit(final BucketUnit baselineUnit) {
      baselineUnit$value = baselineUnit;
      baselineUnit$set = true;
      return this;
    }

    public CustomBucketDto build() {
      boolean active$value = this.active$value;
      if (!active$set) {
        active$value = CustomBucketDto.$default$active();
      }
      Double bucketSize$value = this.bucketSize$value;
      if (!bucketSize$set) {
        bucketSize$value = CustomBucketDto.$default$bucketSize();
      }
      BucketUnit bucketSizeUnit$value = this.bucketSizeUnit$value;
      if (!bucketSizeUnit$set) {
        bucketSizeUnit$value = CustomBucketDto.$default$bucketSizeUnit();
      }
      Double baseline$value = this.baseline$value;
      if (!baseline$set) {
        baseline$value = CustomBucketDto.$default$baseline();
      }
      BucketUnit baselineUnit$value = this.baselineUnit$value;
      if (!baselineUnit$set) {
        baselineUnit$value = CustomBucketDto.$default$baselineUnit();
      }
      return new CustomBucketDto(
          active$value, bucketSize$value, bucketSizeUnit$value, baseline$value, baselineUnit$value);
    }

    @Override
    public String toString() {
      return "CustomBucketDto.CustomBucketDtoBuilder(active$value="
          + active$value
          + ", bucketSize$value="
          + bucketSize$value
          + ", bucketSizeUnit$value="
          + bucketSizeUnit$value
          + ", baseline$value="
          + baseline$value
          + ", baselineUnit$value="
          + baselineUnit$value
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
