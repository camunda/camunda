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

public final class FixedInstanceDateFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private FixedInstanceDateFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public FixedInstanceDateFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new FixedInstanceDateFilterBuilder(filterBuilder);
  }

  static FixedInstanceDateFilterBuilder endDate(final ProcessFilterBuilder filterBuilder) {
    final FixedInstanceDateFilterBuilder builder =
        new FixedInstanceDateFilterBuilder(filterBuilder);
    builder.type = "endDate";
    return builder;
  }

  static FixedInstanceDateFilterBuilder startDate(final ProcessFilterBuilder filterBuilder) {
    final FixedInstanceDateFilterBuilder builder =
        new FixedInstanceDateFilterBuilder(filterBuilder);
    builder.type = "startDate";
    return builder;
  }

  public FixedInstanceDateFilterBuilder start(final OffsetDateTime start) {
    this.start = start;
    return this;
  }

  public FixedInstanceDateFilterBuilder end(final OffsetDateTime end) {
    this.end = end;
    return this;
  }

  public FixedInstanceDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final ProcessFilterDto<DateFilterDataDto<?>> filterDto;
    filterDto =
        "endDate".equals(type) ? new InstanceEndDateFilterDto() : new InstanceStartDateFilterDto();
    filterDto.setData(new FixedDateFilterDataDto(start, end));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }
}
