/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HeatmapTargetValueDto {

  private Boolean active = false;
  private Map<String, HeatmapTargetValueEntryDto> values = new HashMap<>();

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public Map<String, HeatmapTargetValueEntryDto> getValues() {
    return values;
  }

  public void setValues(Map<String, HeatmapTargetValueEntryDto> values) {
    this.values = values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HeatmapTargetValueDto)) {
      return false;
    }
    HeatmapTargetValueDto that = (HeatmapTargetValueDto) o;
    return Objects.equals(active, that.active) &&
      Objects.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, values);
  }
}
