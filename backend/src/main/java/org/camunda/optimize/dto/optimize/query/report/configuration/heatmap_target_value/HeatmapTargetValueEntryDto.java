package org.camunda.optimize.dto.optimize.query.report.configuration.heatmap_target_value;

import org.camunda.optimize.dto.optimize.query.report.configuration.target_value.TargetValueUnit;

public class HeatmapTargetValueEntryDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private Double value = 2.0;

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(TargetValueUnit unit) {
    this.unit = unit;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }
}
