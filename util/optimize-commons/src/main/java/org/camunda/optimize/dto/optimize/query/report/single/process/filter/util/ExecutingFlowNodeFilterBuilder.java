/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExecutingFlowNodeFilterBuilder {

  private List<String> values = new ArrayList<>();
  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private ExecutingFlowNodeFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static ExecutingFlowNodeFilterBuilder construct(ProcessFilterBuilder processFilterBuilder) {
    return new ExecutingFlowNodeFilterBuilder(processFilterBuilder);
  }

  public ExecutingFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutingFlowNodeFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutingFlowNodeFilterBuilder filterLevel(FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ExecutingFlowNodeFilterDataDto dataDto = new ExecutingFlowNodeFilterDataDto();
    dataDto.setValues(new ArrayList<>(values));
    ExecutingFlowNodeFilterDto executingFlowNodeFilterDto = new ExecutingFlowNodeFilterDto();
    executingFlowNodeFilterDto.setData(dataDto);
    executingFlowNodeFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(executingFlowNodeFilterDto);
    return filterBuilder;
  }
}
