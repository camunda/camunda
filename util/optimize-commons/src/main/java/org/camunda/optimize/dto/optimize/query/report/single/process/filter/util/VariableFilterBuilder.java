/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DoubleVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.IntegerVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.LongVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.ShortVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class VariableFilterBuilder {
  private ProcessFilterBuilder filterBuilder;
  private VariableType type;
  private List<String> values = new ArrayList<>();
  private String operator;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private String name;
  private boolean filterForUndefined = false;


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

  public VariableFilterBuilder values(List<String> values) {
    if (values == null) {
      this.values = null;
      return this;
    }
    this.values.addAll(values);
    return this;
  }

  public VariableFilterBuilder filterForUndefined() {
    this.filterForUndefined = true;
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

  public VariableFilterBuilder operator(String operator) {
    this.operator = operator;
    return this;
  }

  public VariableFilterBuilder start(OffsetDateTime start) {
    this.start = start;
    return this;
  }

  public VariableFilterBuilder end(OffsetDateTime end) {
    this.end = end;
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

  public ProcessFilterBuilder createBooleanVariableFilter() {
    BooleanVariableFilterDataDto dataDto =
      new BooleanVariableFilterDataDto(values == null || values.isEmpty() ? null : values.get(0));
    dataDto.setName(name);
    dataDto.setType(type);
    dataDto.setFilterForUndefined(filterForUndefined);
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dataDto);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  public ProcessFilterBuilder createVariableDateFilter() {
    DateVariableFilterDataDto dateVariableFilterDataDto = new DateVariableFilterDataDto(start, end);
    dateVariableFilterDataDto.setName(name);
    dateVariableFilterDataDto.setType(type);
    dateVariableFilterDataDto.setFilterForUndefined(filterForUndefined);
    VariableFilterDto filter = new VariableFilterDto();
    filter.setData(dateVariableFilterDataDto);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

  public ProcessFilterBuilder createOperatorMultipleValuesFilter() {
    VariableFilterDto filter = new VariableFilterDto();
    switch (type) {
      case INTEGER:
        filter.setData(new IntegerVariableFilterDataDto(operator, values));
        break;
      case STRING:
        filter.setData(new StringVariableFilterDataDto(operator, values));
        break;
      case DOUBLE:
        filter.setData(new DoubleVariableFilterDataDto(operator, values));
        break;
      case SHORT:
        filter.setData(new ShortVariableFilterDataDto(operator, values));
        break;
      case LONG:
        filter.setData(new LongVariableFilterDataDto(operator, values));
        break;
      default:
        break;
    }
    filter.getData().setName(name);
    filter.getData().setType(type);
    filter.getData().setFilterForUndefined(filterForUndefined);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
