package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import java.util.Objects;

public class StartDateGroupByValueDto implements GroupByValueDto {

  protected GroupByDateUnit unit;

  public GroupByDateUnit getUnit() {
    return unit;
  }

  public void setUnit(GroupByDateUnit unit) {
    this.unit = unit;
  }

  @Override
  public boolean isCombinable(Object o) {
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
