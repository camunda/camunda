/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

public class DecisionFilterUtilHelper {

  public static EvaluationDateFilterDto createFixedEvaluationDateFilter(OffsetDateTime startDate,
                                                                        OffsetDateTime endDate) {
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();

    final FixedDateFilterDataDto fixedDateFilterDataDto = new FixedDateFilterDataDto();
    fixedDateFilterDataDto.setStart(startDate);
    fixedDateFilterDataDto.setEnd(endDate);

    filter.setData(fixedDateFilterDataDto);

    return filter;
  }

  public static EvaluationDateFilterDto createRelativeEvaluationDateFilter(Long value, DateFilterUnit unit) {
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto();
    RelativeDateFilterStartDto evaluationDate = new RelativeDateFilterStartDto(value, unit);

    evaluationDate.setUnit(unit);
    evaluationDate.setValue(value);
    filterData.setStart(evaluationDate);
    filter.setData(filterData);

    return filter;
  }

  public static EvaluationDateFilterDto createRollingEvaluationDateFilter(Long value, DateFilterUnit unit) {
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    RollingDateFilterDataDto filterData = new RollingDateFilterDataDto();
    RollingDateFilterStartDto evaluationDate = new RollingDateFilterStartDto(value, unit);

    evaluationDate.setUnit(unit);
    evaluationDate.setValue(value);
    filterData.setStart(evaluationDate);
    filter.setData(filterData);

    return filter;
  }

  public static InputVariableFilterDto createStringInputVariableFilter(String variableName, String operator,
                                                                       String... variableValues) {
    StringVariableFilterDataDto data = new StringVariableFilterDataDto(operator, Arrays.asList(variableValues));
    data.setName(variableName);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createDoubleInputVariableFilter(String variableName, String operator,
                                                                       String... variableValues) {
    DoubleVariableFilterDataDto data = new DoubleVariableFilterDataDto(operator, Arrays.asList(variableValues));
    data.setName(variableName);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createBooleanInputVariableFilter(String variableName, String variableValue) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(variableValue);
    data.setName(variableName);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createFixedDateInputVariableFilter(String variableName,
                                                                          OffsetDateTime startDate,
                                                                          OffsetDateTime endDate) {
    DateVariableFilterDataDto data = new DateVariableFilterDataDto(startDate, endDate);
    data.setName(variableName);

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createBooleanOutputVariableFilter(String variableName, String variableValue) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(variableValue);
    data.setName(variableName);

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }


  public static VariableFilterDataDto createUndefinedVariableFilterData(String variableName) {
    VariableFilterDataDto data = new StringVariableFilterDataDto("whatever", Collections.emptyList());
    data.setName(variableName);
    data.setFilterForUndefined(true);
    return data;
  }

}
