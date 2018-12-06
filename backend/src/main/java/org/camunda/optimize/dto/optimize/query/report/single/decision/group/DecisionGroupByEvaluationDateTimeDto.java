package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;

public class DecisionGroupByEvaluationDateTimeDto extends DecisionGroupByDto<DecisionGroupByEvaluationDateTimeValueDto> {

  public DecisionGroupByEvaluationDateTimeDto() {
    this.type = DecisionGroupByType.EVALUATION_DATE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getUnit();
  }


}
