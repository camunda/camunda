/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;

public class ExecutedFlowNodeFilterBuilder {

  private FilterOperator operator = IN;
  private final List<String> values = new ArrayList<>();
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private ExecutedFlowNodeFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static ExecutedFlowNodeFilterBuilder construct(ProcessFilterBuilder processFilterBuilder) {
    return new ExecutedFlowNodeFilterBuilder(processFilterBuilder);
  }

  public ExecutedFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder inOperator() {
    operator = IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder operator(FilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder notInOperator() {
    operator = NOT_IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutedFlowNodeFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ExecutedFlowNodeFilterDataDto dataDto = new ExecutedFlowNodeFilterDataDto();
    dataDto.setOperator(operator);
    dataDto.setValues(new ArrayList<>(values));
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setData(dataDto);
    executedFlowNodeFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(executedFlowNodeFilterDto);
    return filterBuilder;
  }
}
