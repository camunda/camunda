/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;

public class TargetDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";
  private Boolean isBelow = false;

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TargetDto targetDto = (TargetDto) o;
    return unit == targetDto.unit
        && Objects.equals(value, targetDto.value)
        && Objects.equals(isBelow, targetDto.isBelow);
  }

  @Override
  public int hashCode() {
    return Objects.hash(unit, value, isBelow);
  }

  @Override
  public String toString() {
    return "TargetDto(unit="
        + getUnit()
        + ", value="
        + getValue()
        + ", isBelow="
        + getIsBelow()
        + ")";
  }

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(final TargetValueUnit unit) {
    this.unit = unit;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  public Boolean getIsBelow() {
    return isBelow;
  }

  public void setIsBelow(final Boolean isBelow) {
    this.isBelow = isBelow;
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String unit = "unit";
    public static final String value = "value";
    public static final String isBelow = "isBelow";
  }
}
