/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;

public class DecisionGroupByOutputVariableDto extends DecisionGroupByDto<DecisionGroupByVariableValueDto> {

  public DecisionGroupByOutputVariableDto() {
    this.type = DecisionGroupByType.OUTPUT_VARIABLE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getId();
  }
}
