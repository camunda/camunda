/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

public class RelativeInstanceDateFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private RelativeDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private RelativeInstanceDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RelativeInstanceDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RelativeInstanceDateFilterBuilder builder = new RelativeInstanceDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  static RelativeInstanceDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RelativeInstanceDateFilterBuilder builder = new RelativeInstanceDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  public RelativeInstanceDateFilterBuilder start(Long value, DateUnit unit) {
    this.start = new RelativeDateFilterStartDto(value, unit);
    return this;
  }

  public RelativeInstanceDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<DateFilterDataDto<?>> filterDto =
      type.equals("endDate") ? new InstanceEndDateFilterDto() : new InstanceStartDateFilterDto();
    filterDto.setData(new RelativeDateFilterDataDto(start));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }
}
