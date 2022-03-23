/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

@Data
public class HeatmapTargetValueEntryDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";

  public HeatmapTargetValueEntryDto() {
  }

  public HeatmapTargetValueEntryDto(final TargetValueUnit unit, final String value) {
    this.unit = unit;
    this.value = value;
  }
}
