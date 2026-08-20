/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.plan.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByNoneDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByOutputVariableDto;

public enum DecisionGroupBy {
  DECISION_GROUP_BY_EVALUATION_DATE_TIME(new DecisionGroupByEvaluationDateTimeDto()),
  DECISION_GROUP_BY_MATCHED_RULE(new DecisionGroupByMatchedRuleDto()),
  DECISION_GROUP_BY_NONE(new DecisionGroupByNoneDto()),
  DECISION_GROUP_BY_INPUT_VARIABLE(new DecisionGroupByInputVariableDto()),
  DECISION_GROUP_BY_OUTPUT_VARIABLE(new DecisionGroupByOutputVariableDto());

  private final DecisionGroupByDto<?> dto;

  private DecisionGroupBy(final DecisionGroupByDto<?> dto) {
    this.dto = dto;
  }

  public DecisionGroupByDto<?> getDto() {
    return this.dto;
  }
}
