/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;

public class DurationFilterBuilder {
  protected final ProcessFilterBuilder filterBuilder;

  protected Long value;
  protected DurationUnit unit;
  protected ComparisonOperator comparisonOperator;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  protected DurationFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static DurationFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new DurationFilterBuilder(filterBuilder);
  }

  public DurationFilterBuilder value(final Long value) {
    this.value = value;
    return this;
  }

  public DurationFilterBuilder unit(final DurationUnit unit) {
    this.unit = unit;
    return this;
  }

  public DurationFilterBuilder operator(final ComparisonOperator comparisonOperator) {
    this.comparisonOperator = comparisonOperator;
    return this;
  }

  public DurationFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final DurationFilterDataDto durationFilterDataDto = new DurationFilterDataDto();
    durationFilterDataDto.setOperator(comparisonOperator);
    durationFilterDataDto.setUnit(unit);
    durationFilterDataDto.setValue(value);
    final DurationFilterDto durationFilterDto = new DurationFilterDto();
    durationFilterDto.setData(durationFilterDataDto);
    durationFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(durationFilterDto);
    return filterBuilder;
  }
}
