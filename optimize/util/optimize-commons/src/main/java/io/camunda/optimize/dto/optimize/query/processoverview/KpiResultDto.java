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
import java.util.Objects;
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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final KpiResultDto that = (KpiResultDto) o;
    return isBelow == that.isBelow
        && Objects.equals(reportId, that.reportId)
        && Objects.equals(collectionId, that.collectionId)
        && Objects.equals(reportName, that.reportName)
        && Objects.equals(value, that.value)
        && Objects.equals(target, that.target)
        && type == that.type
        && Objects.equals(measure, that.measure)
        && unit == that.unit;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        reportId, collectionId, reportName, value, target, isBelow, type, measure, unit);
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

  @SuppressWarnings("checkstyle:ConstantName")
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
