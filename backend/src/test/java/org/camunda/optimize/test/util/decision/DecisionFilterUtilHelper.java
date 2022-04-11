/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DecisionFilterUtilHelper {

  public static EvaluationDateFilterDto createFixedEvaluationDateFilter(OffsetDateTime startDate,
                                                                        OffsetDateTime endDate) {
    final FixedDateFilterDataDto fixedDateFilterDataDto = new FixedDateFilterDataDto(startDate, endDate);
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(fixedDateFilterDataDto);
    return filter;
  }

  public static EvaluationDateFilterDto createRollingEvaluationDateFilter(Long value, DateUnit unit) {
    RollingDateFilterDataDto filterData = new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit));
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(filterData);
    return filter;
  }

  public static EvaluationDateFilterDto createRelativeEvaluationDateFilter (Long value, DateUnit unit) {
    RelativeDateFilterStartDto evaluationDate = new RelativeDateFilterStartDto(value, unit);
    RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto(evaluationDate);
    EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(filterData);
    return filter;
  }

  public static InputVariableFilterDto createStringInputVariableFilter(String variableName,
                                                                       FilterOperator operator,
                                                                       String... variableValues) {
    StringVariableFilterDataDto data = new StringVariableFilterDataDto(
      variableName, operator, Arrays.asList(variableValues)
    );
    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createNumericInputVariableFilter(String variableName,
                                                                        FilterOperator operator,
                                                                        String... variableValues) {
    DoubleVariableFilterDataDto data = new DoubleVariableFilterDataDto(
      variableName,
      operator,
      Arrays.asList(variableValues)
    );
    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createDateInputVariableFilter(final String variableName,
                                                                     final DateFilterDataDto<?> dateFilterDataDto) {
    final DateVariableFilterDataDto data = new DateVariableFilterDataDto(variableName, dateFilterDataDto);
    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createBooleanInputVariableFilter(String variableName, Boolean variableValue) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(variableName, Collections.singletonList(variableValue));
    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createNumericInputVariableFilter(String variableName,
                                                                        VariableType variableType,
                                                                        FilterOperator operator,
                                                                        List<String> variableValues) {
    OperatorMultipleValuesFilterDataDto subData = new OperatorMultipleValuesFilterDataDto(
      operator, variableValues
    );
    OperatorMultipleValuesVariableFilterDataDto data = new OperatorMultipleValuesVariableFilterDataDto(
      variableName, variableType, subData
    );

    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createFixedDateInputVariableFilter(final String variableName,
                                                                          final OffsetDateTime startDate,
                                                                          final OffsetDateTime endDate) {
    return createDateInputVariableFilter(variableName, new FixedDateFilterDataDto(startDate, endDate));
  }

  public static InputVariableFilterDto createRollingDateInputVariableFilter(final String variableName,
                                                                            final Long value,
                                                                            final DateUnit unit) {
    return createDateInputVariableFilter(
      variableName, new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit))
    );
  }

  public static InputVariableFilterDto createRelativeDateInputVariableFilter(final String variableName,
                                                                            final Long value,
                                                                            final DateUnit unit) {
    return createDateInputVariableFilter(
      variableName, new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(value, unit))
    );
  }

  public static InputVariableFilterDto createBooleanInputVariableFilter(final String variableName,
                                                                         final List<Boolean> variableValues) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(variableName, variableValues);
    InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createBooleanOutputVariableFilter(final String variableName,
                                                                          final List<Boolean> variableValues) {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto(variableName, variableValues);
    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createStringOutputVariableFilter(String variableName,
                                                                         FilterOperator operator,
                                                                         String... variableValues) {
    StringVariableFilterDataDto data = new StringVariableFilterDataDto(
      variableName,
      operator,
      Arrays.asList(variableValues)
    );

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createNumericOutputVariableFilter(String variableName,
                                                                          VariableType variableType,
                                                                          FilterOperator operator,
                                                                          List<String> variableValues) {
    OperatorMultipleValuesFilterDataDto subData = new OperatorMultipleValuesFilterDataDto(
      operator, variableValues
    );
    OperatorMultipleValuesVariableFilterDataDto data = new OperatorMultipleValuesVariableFilterDataDto(
      variableName, variableType, subData
    );

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createFixedDateOutputVariableFilter(String variableName,
                                                                          OffsetDateTime startDate,
                                                                          OffsetDateTime endDate) {
    DateVariableFilterDataDto data = new DateVariableFilterDataDto(
      variableName,
      new FixedDateFilterDataDto(startDate, endDate)
    );

    OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

}
