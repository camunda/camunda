/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
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
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.IntegerVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.ShortVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class VariableFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private VariableType type;
  private List<String> values = new ArrayList<>();
  private FilterOperator operator;
  private DateFilterDataDto<?> dateFilterDataDto;
  private String name;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private VariableFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static VariableFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new VariableFilterBuilder(filterBuilder);
  }

  public VariableFilterBuilder type(final VariableType type) {
    this.type = type;
    return this;
  }

  public VariableFilterBuilder booleanType() {
    type = VariableType.BOOLEAN;
    return this;
  }

  public VariableFilterBuilder shortType() {
    type = VariableType.SHORT;
    return this;
  }

  public VariableFilterBuilder integerType() {
    type = VariableType.INTEGER;
    return this;
  }

  public VariableFilterBuilder longType() {
    type = VariableType.LONG;
    return this;
  }

  public VariableFilterBuilder doubleType() {
    type = VariableType.DOUBLE;
    return this;
  }

  public VariableFilterBuilder stringType() {
    type = VariableType.STRING;
    return this;
  }

  public VariableFilterBuilder dateType() {
    type = VariableType.DATE;
    return this;
  }

  public VariableFilterBuilder name(final String name) {
    this.name = name;
    return this;
  }

  public VariableFilterBuilder booleanValues(final List<Boolean> values) {
    values(
        Optional.ofNullable(values)
            .map(
                theValues ->
                    theValues.stream()
                        .map(aBoolean -> aBoolean != null ? aBoolean.toString() : null)
                        .collect(Collectors.toList()))
            .orElse(null));
    return this;
  }

  public VariableFilterBuilder value(final String value) {
    return values(List.of(value));
  }

  public VariableFilterBuilder values(final List<String> values) {
    if (values == null) {
      this.values = null;
      return this;
    }
    this.values.addAll(values);
    return this;
  }

  public VariableFilterBuilder booleanTrue() {
    type = VariableType.BOOLEAN;
    values.add("true");
    return this;
  }

  public VariableFilterBuilder booleanFalse() {
    type = VariableType.BOOLEAN;
    values.add("false");
    return this;
  }

  public VariableFilterBuilder operator(final FilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public VariableFilterBuilder dateFilter(final DateFilterDataDto dateFilterDataDto) {
    this.dateFilterDataDto = dateFilterDataDto;
    return this;
  }

  public VariableFilterBuilder rollingDate(final Long value, final DateUnit unit) {
    dateFilterDataDto = new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder relativeDate(final Long value, final DateUnit unit) {
    dateFilterDataDto = new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder fixedDate(final OffsetDateTime start, final OffsetDateTime end) {
    dateFilterDataDto = new FixedDateFilterDataDto(start, end);
    return this;
  }

  public VariableFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public VariableFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public VariableFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    switch (type) {
      case BOOLEAN:
        return createBooleanVariableFilter();
      case DATE:
        return createVariableDateFilter();
      case LONG:
      case SHORT:
      case DOUBLE:
      case STRING:
      case INTEGER:
        return createOperatorMultipleValuesFilter();
      default:
        return filterBuilder;
    }
  }

  private ProcessFilterBuilder createBooleanVariableFilter() {
    final BooleanVariableFilterDataDto dataDto =
        new BooleanVariableFilterDataDto(
            name,
            Optional.ofNullable(values)
                .map(
                    theValues ->
                        theValues.stream()
                            .map(value -> value != null ? Boolean.valueOf(value) : null)
                            .collect(Collectors.toList()))
                .orElse(null));
    final VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dataDto);
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  private ProcessFilterBuilder createVariableDateFilter() {
    final DateVariableFilterDataDto dateVariableFilterDataDto =
        new DateVariableFilterDataDto(
            name,
            dateFilterDataDto != null ? dateFilterDataDto : new FixedDateFilterDataDto(null, null));
    final VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dateVariableFilterDataDto);
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  private ProcessFilterBuilder createOperatorMultipleValuesFilter() {
    final VariableFilterDto filter = new VariableFilterDto();
    switch (type) {
      case INTEGER:
        filter.setData(new IntegerVariableFilterDataDto(name, operator, values));
        break;
      case STRING:
        filter.setData(new StringVariableFilterDataDto(name, operator, values));
        break;
      case DOUBLE:
        filter.setData(new DoubleVariableFilterDataDto(name, operator, values));
        break;
      case SHORT:
        filter.setData(new ShortVariableFilterDataDto(name, operator, values));
        break;
      case LONG:
        filter.setData(new LongVariableFilterDataDto(name, operator, values));
        break;
      default:
        break;
    }
    filter.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> filter.setAppliedTo(appliedTo));
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
