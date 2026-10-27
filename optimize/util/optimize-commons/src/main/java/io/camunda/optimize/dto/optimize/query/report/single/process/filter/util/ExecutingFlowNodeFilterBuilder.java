/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ExecutingFlowNodeFilterBuilder {

  private final List<String> values = new ArrayList<>();
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private ExecutingFlowNodeFilterBuilder(final ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static ExecutingFlowNodeFilterBuilder construct(
      final ProcessFilterBuilder processFilterBuilder) {
    return new ExecutingFlowNodeFilterBuilder(processFilterBuilder);
  }

  public ExecutingFlowNodeFilterBuilder id(final String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutingFlowNodeFilterBuilder ids(final String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutingFlowNodeFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final ExecutingFlowNodeFilterDataDto dataDto = new ExecutingFlowNodeFilterDataDto();
    dataDto.setValues(new ArrayList<>(values));
    final ExecutingFlowNodeFilterDto executingFlowNodeFilterDto = new ExecutingFlowNodeFilterDto();
    executingFlowNodeFilterDto.setData(dataDto);
    executingFlowNodeFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(executingFlowNodeFilterDto);
    return filterBuilder;
  }
}
