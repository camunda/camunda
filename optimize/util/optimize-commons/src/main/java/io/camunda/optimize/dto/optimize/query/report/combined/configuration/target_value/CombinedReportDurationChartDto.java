/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import java.util.Objects;

public class CombinedReportDurationChartDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Boolean isBelow = false;
  private String value = "2";

  public CombinedReportDurationChartDto(
      final TargetValueUnit unit, final Boolean isBelow, final String value) {
    this.unit = unit;
    this.isBelow = isBelow;
    this.value = value;
  }

  public CombinedReportDurationChartDto() {}

  @Override
  public int hashCode() {
    return Objects.hash(unit, isBelow, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CombinedReportDurationChartDto that)) {
      return false;
    }
    return unit == that.unit
        && Objects.equals(isBelow, that.isBelow)
        && Objects.equals(value, that.value);
  }

  @Override
  public String toString() {
    return "CombinedReportDurationChartDto(unit="
        + getUnit()
        + ", isBelow="
        + getIsBelow()
        + ", value="
        + getValue()
        + ")";
  }

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(final TargetValueUnit unit) {
    this.unit = unit;
  }

  public Boolean getIsBelow() {
    return isBelow;
  }

  public void setIsBelow(final Boolean isBelow) {
    this.isBelow = isBelow;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }
}
