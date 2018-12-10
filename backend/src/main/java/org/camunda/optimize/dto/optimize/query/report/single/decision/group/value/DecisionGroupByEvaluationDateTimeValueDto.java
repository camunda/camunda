package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;

import java.util.Objects;

public class DecisionGroupByEvaluationDateTimeValueDto implements DecisionGroupByValueDto {

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
    if (!(o instanceof DecisionGroupByEvaluationDateTimeValueDto)) {
      return false;
    }
    DecisionGroupByEvaluationDateTimeValueDto that = (DecisionGroupByEvaluationDateTimeValueDto) o;
    return Objects.equals(unit, that.unit);
  }
}
