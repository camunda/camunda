/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;

import java.time.OffsetDateTime;

public class FixedDateFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private FixedDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public FixedDateFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new FixedDateFilterBuilder(filterBuilder);
  }

  static FixedDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    FixedDateFilterBuilder builder = new FixedDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static FixedDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    FixedDateFilterBuilder builder = new FixedDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public FixedDateFilterBuilder start(OffsetDateTime start) {
    this.start = start;
    return this;
  }

  public FixedDateFilterBuilder end(OffsetDateTime end) {
    this.end = end;
    return this;
  }

  public FixedDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<DateFilterDataDto<?>> filterDto;
    filterDto = type.equals("endDate") ? new EndDateFilterDto() : new StartDateFilterDto();
    filterDto.setData(new FixedDateFilterDataDto(start, end));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }

}
