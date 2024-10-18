/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.processoverview;

import static io.camunda.optimize.dto.optimize.query.report.single.ViewProperty.DURATION;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit.mapToChronoUnit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.Duration;
import org.apache.commons.lang3.StringUtils;

public class KpiResultDto {

  private String reportId;
  private String collectionId;
  private String reportName;
  private String value;
  private String target;

  @JsonProperty("isBelow")
  private boolean isBelow;

  private KpiType type;
  private ViewProperty measure;
  private TargetValueUnit unit;

  public KpiResultDto() {}

  @JsonIgnore
  public boolean isTargetMet() {
    if (StringUtils.isBlank(value) || StringUtils.isBlank(target)) {
      return false;
    }
    final double doubleValue;
    final double doubleTarget;
    try {
      doubleValue = Double.parseDouble(value);
      doubleTarget = Double.parseDouble(target);
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException(
          String.format("Error parsing KPI value %s and target %s", value, target));
    }
    if (isBelow) {
      return DURATION.equals(measure)
          ? Duration.ofMillis((long) doubleValue)
                  .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit)))
              <= 0
          : doubleValue <= doubleTarget;
    } else {
      return DURATION.equals(measure)
          ? Duration.ofMillis((long) doubleValue)
                  .compareTo(Duration.of((long) doubleTarget, mapToChronoUnit(unit)))
              >= 0
          : doubleValue >= doubleTarget;
    }
  }

  public String getReportId() {
    return reportId;
  }

  public void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public void setCollectionId(final String collectionId) {
    this.collectionId = collectionId;
  }

  public String getReportName() {
    return reportName;
  }

  public void setReportName(final String reportName) {
    this.reportName = reportName;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(final String target) {
    this.target = target;
  }

  public boolean isBelow() {
    return isBelow;
  }

  @JsonProperty("isBelow")
  public void setBelow(final boolean isBelow) {
    this.isBelow = isBelow;
  }

  public KpiType getType() {
    return type;
  }

  public void setType(final KpiType type) {
    this.type = type;
  }

  public ViewProperty getMeasure() {
    return measure;
  }

  public void setMeasure(final ViewProperty measure) {
    this.measure = measure;
  }

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(final TargetValueUnit unit) {
    this.unit = unit;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof KpiResultDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportId = getReportId();
    result = result * PRIME + ($reportId == null ? 43 : $reportId.hashCode());
    final Object $collectionId = getCollectionId();
    result = result * PRIME + ($collectionId == null ? 43 : $collectionId.hashCode());
    final Object $reportName = getReportName();
    result = result * PRIME + ($reportName == null ? 43 : $reportName.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    final Object $target = getTarget();
    result = result * PRIME + ($target == null ? 43 : $target.hashCode());
    result = result * PRIME + (isBelow() ? 79 : 97);
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $measure = getMeasure();
    result = result * PRIME + ($measure == null ? 43 : $measure.hashCode());
    final Object $unit = getUnit();
    result = result * PRIME + ($unit == null ? 43 : $unit.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof KpiResultDto)) {
      return false;
    }
    final KpiResultDto other = (KpiResultDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportId = getReportId();
    final Object other$reportId = other.getReportId();
    if (this$reportId == null ? other$reportId != null : !this$reportId.equals(other$reportId)) {
      return false;
    }
    final Object this$collectionId = getCollectionId();
    final Object other$collectionId = other.getCollectionId();
    if (this$collectionId == null
        ? other$collectionId != null
        : !this$collectionId.equals(other$collectionId)) {
      return false;
    }
    final Object this$reportName = getReportName();
    final Object other$reportName = other.getReportName();
    if (this$reportName == null
        ? other$reportName != null
        : !this$reportName.equals(other$reportName)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    final Object this$target = getTarget();
    final Object other$target = other.getTarget();
    if (this$target == null ? other$target != null : !this$target.equals(other$target)) {
      return false;
    }
    if (isBelow() != other.isBelow()) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$measure = getMeasure();
    final Object other$measure = other.getMeasure();
    if (this$measure == null ? other$measure != null : !this$measure.equals(other$measure)) {
      return false;
    }
    final Object this$unit = getUnit();
    final Object other$unit = other.getUnit();
    if (this$unit == null ? other$unit != null : !this$unit.equals(other$unit)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "KpiResultDto(reportId="
        + getReportId()
        + ", collectionId="
        + getCollectionId()
        + ", reportName="
        + getReportName()
        + ", value="
        + getValue()
        + ", target="
        + getTarget()
        + ", isBelow="
        + isBelow()
        + ", type="
        + getType()
        + ", measure="
        + getMeasure()
        + ", unit="
        + getUnit()
        + ")";
  }

  public static final class Fields {

    public static final String reportId = "reportId";
    public static final String collectionId = "collectionId";
    public static final String reportName = "reportName";
    public static final String value = "value";
    public static final String target = "target";
    public static final String isBelow = "isBelow";
    public static final String type = "type";
    public static final String measure = "measure";
    public static final String unit = "unit";
  }
}
