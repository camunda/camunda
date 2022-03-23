/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

public class RollingInstanceDateFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private RollingDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private RollingInstanceDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RollingInstanceDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RollingInstanceDateFilterBuilder builder = new RollingInstanceDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static RollingInstanceDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RollingInstanceDateFilterBuilder builder = new RollingInstanceDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public RollingInstanceDateFilterBuilder start(Long value, DateUnit unit) {
    this.start = new RollingDateFilterStartDto(value, unit);
    return this;
  }

  public RollingInstanceDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<DateFilterDataDto<?>> filterDto =
      type.equals("endDate") ? new InstanceEndDateFilterDto() : new InstanceStartDateFilterDto();
    filterDto.setData(new RollingDateFilterDataDto(start));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }

}
