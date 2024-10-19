/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined.configuration.target_value;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
