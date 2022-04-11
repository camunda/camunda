/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.FixedDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

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
    filterDto = type.equals("endDate") ? new InstanceEndDateFilterDto() : new InstanceStartDateFilterDto();
    filterDto.setData(new FixedDateFilterDataDto(start, end));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }

}
