/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DecisionFilterUtilHelper {

  public static EvaluationDateFilterDto createFixedEvaluationDateFilter(
      final OffsetDateTime startDate, final OffsetDateTime endDate) {
    final FixedDateFilterDataDto fixedDateFilterDataDto =
        new FixedDateFilterDataDto(startDate, endDate);
    final EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(fixedDateFilterDataDto);
    return filter;
  }

  public static EvaluationDateFilterDto createRollingEvaluationDateFilter(
      final Long value, final DateUnit unit) {
    final RollingDateFilterDataDto filterData =
        new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit));
    final EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(filterData);
    return filter;
  }

  public static EvaluationDateFilterDto createRelativeEvaluationDateFilter(
      final Long value, final DateUnit unit) {
    final RelativeDateFilterStartDto evaluationDate = new RelativeDateFilterStartDto(value, unit);
    final RelativeDateFilterDataDto filterData = new RelativeDateFilterDataDto(evaluationDate);
    final EvaluationDateFilterDto filter = new EvaluationDateFilterDto();
    filter.setData(filterData);
    return filter;
  }

  public static InputVariableFilterDto createStringInputVariableFilter(
      final String variableName, final FilterOperator operator, final String... variableValues) {
    final StringVariableFilterDataDto data =
        new StringVariableFilterDataDto(variableName, operator, Arrays.asList(variableValues));
    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createNumericInputVariableFilter(
      final String variableName, final FilterOperator operator, final String... variableValues) {
    final DoubleVariableFilterDataDto data =
        new DoubleVariableFilterDataDto(variableName, operator, Arrays.asList(variableValues));
    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createDateInputVariableFilter(
      final String variableName, final DateFilterDataDto<?> dateFilterDataDto) {
    final DateVariableFilterDataDto data =
        new DateVariableFilterDataDto(variableName, dateFilterDataDto);
    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createBooleanInputVariableFilter(
      final String variableName, final Boolean variableValue) {
    final BooleanVariableFilterDataDto data =
        new BooleanVariableFilterDataDto(variableName, Collections.singletonList(variableValue));
    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createNumericInputVariableFilter(
      final String variableName,
      final VariableType variableType,
      final FilterOperator operator,
      final List<String> variableValues) {
    final OperatorMultipleValuesFilterDataDto subData =
        new OperatorMultipleValuesFilterDataDto(operator, variableValues);
    final OperatorMultipleValuesVariableFilterDataDto data =
        new OperatorMultipleValuesVariableFilterDataDto(variableName, variableType, subData);

    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static InputVariableFilterDto createFixedDateInputVariableFilter(
      final String variableName, final OffsetDateTime startDate, final OffsetDateTime endDate) {
    return createDateInputVariableFilter(
        variableName, new FixedDateFilterDataDto(startDate, endDate));
  }

  public static InputVariableFilterDto createRollingDateInputVariableFilter(
      final String variableName, final Long value, final DateUnit unit) {
    return createDateInputVariableFilter(
        variableName, new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit)));
  }

  public static InputVariableFilterDto createRelativeDateInputVariableFilter(
      final String variableName, final Long value, final DateUnit unit) {
    return createDateInputVariableFilter(
        variableName, new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(value, unit)));
  }

  public static InputVariableFilterDto createBooleanInputVariableFilter(
      final String variableName, final List<Boolean> variableValues) {
    final BooleanVariableFilterDataDto data =
        new BooleanVariableFilterDataDto(variableName, variableValues);
    final InputVariableFilterDto variableFilterDto = new InputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createBooleanOutputVariableFilter(
      final String variableName, final List<Boolean> variableValues) {
    final BooleanVariableFilterDataDto data =
        new BooleanVariableFilterDataDto(variableName, variableValues);
    final OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createStringOutputVariableFilter(
      final String variableName, final FilterOperator operator, final String... variableValues) {
    final StringVariableFilterDataDto data =
        new StringVariableFilterDataDto(variableName, operator, Arrays.asList(variableValues));

    final OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createNumericOutputVariableFilter(
      final String variableName,
      final VariableType variableType,
      final FilterOperator operator,
      final List<String> variableValues) {
    final OperatorMultipleValuesFilterDataDto subData =
        new OperatorMultipleValuesFilterDataDto(operator, variableValues);
    final OperatorMultipleValuesVariableFilterDataDto data =
        new OperatorMultipleValuesVariableFilterDataDto(variableName, variableType, subData);

    final OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }

  public static OutputVariableFilterDto createFixedDateOutputVariableFilter(
      final String variableName, final OffsetDateTime startDate, final OffsetDateTime endDate) {
    final DateVariableFilterDataDto data =
        new DateVariableFilterDataDto(variableName, new FixedDateFilterDataDto(startDate, endDate));

    final OutputVariableFilterDto variableFilterDto = new OutputVariableFilterDto();
    variableFilterDto.setData(data);

    return variableFilterDto;
  }
}
