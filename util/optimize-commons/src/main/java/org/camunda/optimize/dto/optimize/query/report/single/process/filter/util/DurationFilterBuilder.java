/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;

public class DurationFilterBuilder {
  protected final ProcessFilterBuilder filterBuilder;

  protected Long value;
  protected DurationFilterUnit unit;
  protected FilterOperator operator;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  protected DurationFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static DurationFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new DurationFilterBuilder(filterBuilder);
  }

  public DurationFilterBuilder value(Long value) {
    this.value = value;
    return this;
  }

  public DurationFilterBuilder unit(DurationFilterUnit unit) {
    this.unit = unit;
    return this;
  }

  public DurationFilterBuilder operator(FilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public DurationFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    DurationFilterDataDto durationFilterDataDto = new DurationFilterDataDto();
    durationFilterDataDto.setOperator(operator);
    durationFilterDataDto.setUnit(unit);
    durationFilterDataDto.setValue(value);
    DurationFilterDto durationFilterDto = new DurationFilterDto();
    durationFilterDto.setData(durationFilterDataDto);
    durationFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(durationFilterDto);
    return filterBuilder;
  }
}
