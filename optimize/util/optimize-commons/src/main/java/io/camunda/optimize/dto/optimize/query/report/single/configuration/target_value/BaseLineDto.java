/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value;

import java.util.Objects;

public class BaseLineDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "0";

  @Override
  public int hashCode() {
    return Objects.hash(unit, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final BaseLineDto that)) {
      return false;
    }
    return unit == that.unit && Objects.equals(value, that.value);
  }

  @Override
  public String toString() {
    return "BaseLineDto(unit=" + getUnit() + ", value=" + getValue() + ")";
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
}
