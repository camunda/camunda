/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;

public class FlowNodeDurationFilterBuilder extends DurationFilterBuilder {

  private final FlowNodeDurationFiltersDataDto flowNodeFilters = new FlowNodeDurationFiltersDataDto();
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

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
  public FlowNodeDurationFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  @Override
  public ProcessFilterBuilder add() {
    final FlowNodeDurationFilterDto filter = new FlowNodeDurationFilterDto();
    filter.setData(flowNodeFilters);
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
