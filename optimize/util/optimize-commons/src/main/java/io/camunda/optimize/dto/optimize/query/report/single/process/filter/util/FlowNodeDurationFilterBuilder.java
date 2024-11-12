/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;

public final class FlowNodeDurationFilterBuilder extends DurationFilterBuilder {

  private final FlowNodeDurationFiltersDataDto flowNodeFilters =
      new FlowNodeDurationFiltersDataDto();
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private FlowNodeDurationFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    super(filterBuilder);
  }

  public static FlowNodeDurationFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new FlowNodeDurationFilterBuilder(filterBuilder);
  }

  public FlowNodeDurationFilterBuilder flowNode(
      final String flowNodeId, final DurationFilterDataDto filter) {
    flowNodeFilters.put(flowNodeId, filter);
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
