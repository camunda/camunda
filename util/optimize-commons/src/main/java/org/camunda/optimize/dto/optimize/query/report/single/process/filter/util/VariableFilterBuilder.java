/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
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
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.IntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.ShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class VariableFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private VariableType type;
  private List<String> values = new ArrayList<>();
  private FilterOperator operator;
  private DateFilterDataDto<?> dateFilterDataDto;
  private String name;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private VariableFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static VariableFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new VariableFilterBuilder(filterBuilder);
  }

  public VariableFilterBuilder type(VariableType type) {
    this.type = type;
    return this;
  }

  public VariableFilterBuilder booleanType() {
    this.type = VariableType.BOOLEAN;
    return this;
  }

  public VariableFilterBuilder shortType() {
    this.type = VariableType.SHORT;
    return this;
  }

  public VariableFilterBuilder integerType() {
    this.type = VariableType.INTEGER;
    return this;
  }

  public VariableFilterBuilder longType() {
    this.type = VariableType.LONG;
    return this;
  }

  public VariableFilterBuilder doubleType() {
    this.type = VariableType.DOUBLE;
    return this;
  }

  public VariableFilterBuilder stringType() {
    this.type = VariableType.STRING;
    return this;
  }

  public VariableFilterBuilder dateType() {
    this.type = VariableType.DATE;
    return this;
  }

  public VariableFilterBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VariableFilterBuilder booleanValues(final List<Boolean> values) {
    values(
      Optional.ofNullable(values)
        .map(theValues -> theValues.stream()
          .map(aBoolean -> aBoolean != null ? aBoolean.toString() : null)
          .collect(Collectors.toList())
        ).orElse(null)
    );
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
    this.type = VariableType.BOOLEAN;
    this.values.add("true");
    return this;
  }

  public VariableFilterBuilder booleanFalse() {
    this.type = VariableType.BOOLEAN;
    this.values.add("false");
    return this;
  }

  public VariableFilterBuilder operator(FilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public VariableFilterBuilder dateFilter(final DateFilterDataDto dateFilterDataDto) {
    this.dateFilterDataDto = dateFilterDataDto;
    return this;
  }

  public VariableFilterBuilder rollingDate(final Long value, final DateUnit unit) {
    this.dateFilterDataDto = new RollingDateFilterDataDto(new RollingDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder relativeDate(final Long value, final DateUnit unit) {
    this.dateFilterDataDto = new RelativeDateFilterDataDto(new RelativeDateFilterStartDto(value, unit));
    return this;
  }

  public VariableFilterBuilder fixedDate(final OffsetDateTime start, final OffsetDateTime end) {
    this.dateFilterDataDto = new FixedDateFilterDataDto(start, end);
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
    final BooleanVariableFilterDataDto dataDto = new BooleanVariableFilterDataDto(
      name,
      Optional.ofNullable(values)
        .map(theValues -> theValues.stream()
          .map(value -> value != null ? Boolean.valueOf(value) : null)
          .collect(Collectors.toList()))
        .orElse(null)
    );
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dataDto);
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  private ProcessFilterBuilder createVariableDateFilter() {
    DateVariableFilterDataDto dateVariableFilterDataDto = new DateVariableFilterDataDto(
      name,
      dateFilterDataDto != null ? dateFilterDataDto : new FixedDateFilterDataDto(null, null)
    );
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dateVariableFilterDataDto);
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  private ProcessFilterBuilder createOperatorMultipleValuesFilter() {
    VariableFilterDto filter = new VariableFilterDto();
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
