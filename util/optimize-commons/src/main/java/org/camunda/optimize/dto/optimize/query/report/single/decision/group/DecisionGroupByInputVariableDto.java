/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;

public class DecisionGroupByInputVariableDto extends DecisionGroupByDto<DecisionGroupByVariableValueDto> {

  public DecisionGroupByInputVariableDto() {
    this.type = DecisionGroupByType.INPUT_VARIABLE;
  }

  @Override
  public String toString() {
    return super.toString() + "_" + this.getValue().getId();
  }
}
