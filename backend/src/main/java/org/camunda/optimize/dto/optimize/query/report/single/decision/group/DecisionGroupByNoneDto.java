package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByNoneValueDto;

public class DecisionGroupByNoneDto
  extends DecisionGroupByDto<DecisionGroupByNoneValueDto> {

  public DecisionGroupByNoneDto() {
    this.type = DecisionGroupByType.NONE;
  }
}
