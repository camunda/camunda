/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
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
