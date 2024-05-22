/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;

public class DurationFilterBuilder {
  protected final ProcessFilterBuilder filterBuilder;

  protected Long value;
  protected DurationUnit unit;
  protected ComparisonOperator comparisonOperator;
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

  public DurationFilterBuilder unit(DurationUnit unit) {
    this.unit = unit;
    return this;
  }

  public DurationFilterBuilder operator(ComparisonOperator comparisonOperator) {
    this.comparisonOperator = comparisonOperator;
    return this;
  }

  public DurationFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    DurationFilterDataDto durationFilterDataDto = new DurationFilterDataDto();
    durationFilterDataDto.setOperator(comparisonOperator);
    durationFilterDataDto.setUnit(unit);
    durationFilterDataDto.setValue(value);
    DurationFilterDto durationFilterDto = new DurationFilterDto();
    durationFilterDto.setData(durationFilterDataDto);
    durationFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(durationFilterDto);
    return filterBuilder;
  }
}
