package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByNoneValueDto;

public class DecisionGroupByMatchedRuleDto
  extends DecisionGroupByDto<DecisionGroupByNoneValueDto> {

  public DecisionGroupByMatchedRuleDto() {
    this.type = DecisionGroupByType.MATCHED_RULE;
  }
}
