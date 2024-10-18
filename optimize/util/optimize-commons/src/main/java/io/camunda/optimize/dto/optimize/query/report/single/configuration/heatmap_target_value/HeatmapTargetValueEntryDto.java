/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

public class HeatmapTargetValueEntryDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";

  public HeatmapTargetValueEntryDto() {}

  public HeatmapTargetValueEntryDto(final TargetValueUnit unit, final String value) {
    this.unit = unit;
    this.value = value;
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

  protected boolean canEqual(final Object other) {
    return other instanceof HeatmapTargetValueEntryDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $unit = getUnit();
    result = result * PRIME + ($unit == null ? 43 : $unit.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof HeatmapTargetValueEntryDto)) {
      return false;
    }
    final HeatmapTargetValueEntryDto other = (HeatmapTargetValueEntryDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$unit = getUnit();
    final Object other$unit = other.getUnit();
    if (this$unit == null ? other$unit != null : !this$unit.equals(other$unit)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "HeatmapTargetValueEntryDto(unit=" + getUnit() + ", value=" + getValue() + ")";
  }
}
