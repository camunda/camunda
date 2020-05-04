/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;

public class FlowNodeDurationFilterBuilder extends DurationFilterBuilder {

  private final FlowNodeDurationFiltersDataDto flowNodeFilters = new FlowNodeDurationFiltersDataDto();

  private FlowNodeDurationFilterBuilder(ProcessFilterBuilder filterBuilder) {
    super(filterBuilder);
  }

  public static FlowNodeDurationFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new FlowNodeDurationFilterBuilder(filterBuilder);
  }

  public FlowNodeDurationFilterBuilder flowNode(String flowNodeId, DurationFilterDataDto filter) {
    this.flowNodeFilters.put(flowNodeId, filter);
    return this;
  }

  @Override
  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new FlowNodeDurationFilterDto(flowNodeFilters));
    return filterBuilder;
  }
}
