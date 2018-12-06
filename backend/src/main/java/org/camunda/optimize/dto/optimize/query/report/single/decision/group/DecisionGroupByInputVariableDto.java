package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;

public class DecisionGroupByInputVariableDto extends DecisionGroupByDto<DecisionGroupByVariableValueDto> {

  public DecisionGroupByInputVariableDto() {
    this.type = DecisionGroupByType.INPUT_VARIABLE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getName() + "_" + getValue().getType();
  }
}
