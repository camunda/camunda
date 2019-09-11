/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.group;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByNoneValueDto;

public class DecisionGroupByMatchedRuleDto
  extends DecisionGroupByDto<DecisionGroupByNoneValueDto> {

  public DecisionGroupByMatchedRuleDto() {
    this.type = DecisionGroupByType.MATCHED_RULE;
  }
}
