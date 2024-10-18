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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "HeatmapTargetValueDto(active=" + getActive() + ", values=" + getValues() + ")";
  }
}
