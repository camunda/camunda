/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RelativeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

public class RelativeInstanceDateFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private RelativeDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private RelativeInstanceDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RelativeInstanceDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RelativeInstanceDateFilterBuilder builder =
        new RelativeInstanceDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  static RelativeInstanceDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RelativeInstanceDateFilterBuilder builder =
        new RelativeInstanceDateFilterBuilder(filterBuilder);
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
