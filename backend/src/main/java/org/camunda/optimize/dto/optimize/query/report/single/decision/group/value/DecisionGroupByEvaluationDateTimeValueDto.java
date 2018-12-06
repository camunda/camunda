package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import java.util.Objects;

public class DecisionGroupByEvaluationDateTimeValueDto implements DecisionGroupByValueDto {

  protected String unit;

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByEvaluationDateTimeValueDto)) {
      return false;
    }
    DecisionGroupByEvaluationDateTimeValueDto that = (DecisionGroupByEvaluationDateTimeValueDto) o;
    return Objects.equals(unit, that.unit);
  }
}
