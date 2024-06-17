/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import lombok.Data;

@Data
public class HeatmapTargetValueEntryDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";

  public HeatmapTargetValueEntryDto() {}

  public HeatmapTargetValueEntryDto(final TargetValueUnit unit, final String value) {
    this.unit = unit;
    this.value = value;
  }
}
