package org.camunda.optimize.dto.optimize.query.report.single.configuration.heatmap_target_value;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;

public class HeatmapTargetValueEntryDto {

  private TargetValueUnit unit = TargetValueUnit.HOURS;
  private String value = "2";

  public HeatmapTargetValueEntryDto() {
  }

  public HeatmapTargetValueEntryDto(final TargetValueUnit unit, final String value) {
    this.unit = unit;
    this.value = value;
  }

  public TargetValueUnit getUnit() {
    return unit;
  }

  public void setUnit(TargetValueUnit unit) {
    this.unit = unit;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
