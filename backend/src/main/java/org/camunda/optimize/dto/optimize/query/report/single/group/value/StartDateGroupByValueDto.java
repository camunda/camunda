package org.camunda.optimize.dto.optimize.query.report.single.group.value;

import java.util.Objects;

public class StartDateGroupByValueDto implements GroupByValueDto {

  protected String unit;

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StartDateGroupByValueDto)) {
      return false;
    }
    StartDateGroupByValueDto that = (StartDateGroupByValueDto) o;
    return Objects.equals(unit, that.unit);
  }
}
