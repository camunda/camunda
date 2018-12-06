package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

public class DecisionGroupByNoneValueDto implements DecisionGroupByValueDto {

  @Override
  public boolean isCombinable(Object o) {
    return true;
  }
}
