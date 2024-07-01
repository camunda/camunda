/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.time.OffsetDateTime;

public class FixedInstanceDateFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private FixedInstanceDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public FixedInstanceDateFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new FixedInstanceDateFilterBuilder(filterBuilder);
  }

  static FixedInstanceDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    FixedInstanceDateFilterBuilder builder = new FixedInstanceDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static FixedInstanceDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    FixedInstanceDateFilterBuilder builder = new FixedInstanceDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public FixedInstanceDateFilterBuilder start(OffsetDateTime start) {
    this.start = start;
    return this;
  }

  public FixedInstanceDateFilterBuilder end(OffsetDateTime end) {
    this.end = end;
    return this;
  }

  public FixedInstanceDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<DateFilterDataDto<?>> filterDto;
    filterDto =
        type.equals("endDate") ? new InstanceEndDateFilterDto() : new InstanceStartDateFilterDto();
    filterDto.setData(new FixedDateFilterDataDto(start, end));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }
}
