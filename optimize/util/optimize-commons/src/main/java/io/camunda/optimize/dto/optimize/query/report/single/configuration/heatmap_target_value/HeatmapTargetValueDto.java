/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import java.util.HashMap;
import java.util.Map;

public class HeatmapTargetValueDto {

  private Boolean active = false;
  private Map<String, HeatmapTargetValueEntryDto> values = new HashMap<>();

  public HeatmapTargetValueDto() {}

  public Boolean getActive() {
    return active;
  }

  public void setActive(final Boolean active) {
    this.active = active;
  }

  public Map<String, HeatmapTargetValueEntryDto> getValues() {
    return values;
  }

  public void setValues(final Map<String, HeatmapTargetValueEntryDto> values) {
    this.values = values;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof HeatmapTargetValueDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $active = getActive();
    result = result * PRIME + ($active == null ? 43 : $active.hashCode());
    final Object $values = getValues();
    result = result * PRIME + ($values == null ? 43 : $values.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof HeatmapTargetValueDto)) {
      return false;
    }
    final HeatmapTargetValueDto other = (HeatmapTargetValueDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$active = getActive();
    final Object other$active = other.getActive();
    if (this$active == null ? other$active != null : !this$active.equals(other$active)) {
      return false;
    }
    final Object this$values = getValues();
    final Object other$values = other.getValues();
    if (this$values == null ? other$values != null : !this$values.equals(other$values)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "HeatmapTargetValueDto(active=" + getActive() + ", values=" + getValues() + ")";
  }
}
