/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;

public class RollingDateFilterBuilder {
  private ProcessFilterBuilder filterBuilder;
  private RollingDateFilterStartDto start;
  private String type;

  private RollingDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RollingDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RollingDateFilterBuilder builder = new RollingDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  static RollingDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RollingDateFilterBuilder builder = new RollingDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  public RollingDateFilterBuilder start(Long value, DateFilterUnit unit) {
    this.start = new RollingDateFilterStartDto(value, unit);
    return this;
  }

  public ProcessFilterBuilder add() {
    RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto();
    dateFilterDataDto.setStart(start);
    if (type.equals("endDate")) {
      EndDateFilterDto filterDto = new EndDateFilterDto(dateFilterDataDto);
      filterBuilder.addFilter(filterDto);
      return filterBuilder;
    } else {
      StartDateFilterDto filterDto = new StartDateFilterDto(dateFilterDataDto);
      filterBuilder.addFilter(filterDto);
      return filterBuilder;
    }
  }
}
