/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFilterDataDto;

public class FlowNodeDurationFilterBuilder extends DurationFilterBuilder {

  private String flowNodeId;

  private FlowNodeDurationFilterBuilder(ProcessFilterBuilder filterBuilder) {
    super(filterBuilder);
  }

  public static FlowNodeDurationFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new FlowNodeDurationFilterBuilder(filterBuilder);
  }

  public FlowNodeDurationFilterBuilder flowNodeId(String flowNodeId) {
    this.flowNodeId = flowNodeId;
    return this;
  }

  @Override
  public ProcessFilterBuilder add() {
    final FlowNodeDurationFilterDataDto durationFilterDataDto = FlowNodeDurationFilterDataDto.builder()
      .flowNodeId(flowNodeId)
      .operator(operator)
      .unit(unit)
      .value(value)
      .build();
    filterBuilder.addFilter(new FlowNodeDurationFilterDto(durationFilterDataDto));
    return filterBuilder;
  }
}
