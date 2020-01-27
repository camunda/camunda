/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;

public class RelativeDateFilterBuilder {
  private ProcessFilterBuilder filterBuilder;
  private RelativeDateFilterStartDto start;
  private String type;

  private RelativeDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static RelativeDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RelativeDateFilterBuilder builder = new RelativeDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static RelativeDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RelativeDateFilterBuilder builder = new RelativeDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public RelativeDateFilterBuilder start(Long value, DateFilterUnit unit) {
    this.start = new RelativeDateFilterStartDto(value, unit);
    return this;
  }

  public ProcessFilterBuilder add() {
    RelativeDateFilterDataDto dateFilterDataDto = new RelativeDateFilterDataDto();
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
