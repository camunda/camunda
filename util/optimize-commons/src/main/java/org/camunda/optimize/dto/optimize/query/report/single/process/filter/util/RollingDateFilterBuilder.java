/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;

public class RollingDateFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private RollingDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private RollingDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RollingDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RollingDateFilterBuilder builder = new RollingDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static RollingDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RollingDateFilterBuilder builder = new RollingDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public RollingDateFilterBuilder start(Long value, DateFilterUnit unit) {
    this.start = new RollingDateFilterStartDto(value, unit);
    return this;
  }

  public RollingDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<DateFilterDataDto<?>> filterDto =
      type.equals("endDate") ? new EndDateFilterDto() : new StartDateFilterDto();
    filterDto.setData(new RollingDateFilterDataDto(start));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }

}
