/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByEvaluationDateTimeDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByNoneDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByOutputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByEvaluationDateTimeValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;


public class DecisionGroupByDtoCreator {

  public static DecisionGroupByDto createGroupDecisionByNone() {
    return new DecisionGroupByNoneDto();
  }

  public static DecisionGroupByDto createGroupDecisionByEvaluationDateTime() {
    return createGroupDecisionByEvaluationDateTime(null);
  }

  public static DecisionGroupByDto createGroupDecisionByEvaluationDateTime(GroupByDateUnit groupByDateUnit) {
    DecisionGroupByEvaluationDateTimeDto groupByDto = new DecisionGroupByEvaluationDateTimeDto();
    DecisionGroupByEvaluationDateTimeValueDto valueDto = new DecisionGroupByEvaluationDateTimeValueDto();
    valueDto.setUnit(groupByDateUnit);
    groupByDto.setValue(valueDto);

    return groupByDto;
  }

  public static DecisionGroupByDto createGroupDecisionByInputVariable() {
    return createGroupDecisionByInputVariable(null, null);
  }

  public static DecisionGroupByDto createGroupDecisionByInputVariable(String variableId, String variableName) {
    DecisionGroupByVariableValueDto groupByValueDto = new DecisionGroupByVariableValueDto();
    groupByValueDto.setId(variableId);
    groupByValueDto.setName(variableName);
    DecisionGroupByInputVariableDto groupByDto = new DecisionGroupByInputVariableDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static DecisionGroupByDto createGroupDecisionByOutputVariable() {
    return createGroupDecisionByOutputVariable(null, null);
  }

  public static DecisionGroupByDto createGroupDecisionByOutputVariable(String variableId, String variableName) {
    DecisionGroupByVariableValueDto groupByValueDto = new DecisionGroupByVariableValueDto();
    groupByValueDto.setId(variableId);
    groupByValueDto.setName(variableName);
    DecisionGroupByOutputVariableDto groupByDto = new DecisionGroupByOutputVariableDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

}
