/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FixedFlowNodeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.time.OffsetDateTime;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;

public class FixedFlowNodeDateFilterBuilder {
  private final ProcessFilterBuilder filterBuilder;
  private List<String> flowNodeIds;
  private OffsetDateTime start;
  private OffsetDateTime end;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.VIEW;

  private FixedFlowNodeDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static FixedFlowNodeDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    FixedFlowNodeDateFilterBuilder builder = new FixedFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_START_DATE;
    return builder;
  }

  public static FixedFlowNodeDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    FixedFlowNodeDateFilterBuilder builder = new FixedFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_END_DATE;
    return builder;
  }

  public FixedFlowNodeDateFilterBuilder flowNodeIds(final List<String> flowNodeIds) {
    this.flowNodeIds = flowNodeIds;
    return this;
  }

  public FixedFlowNodeDateFilterBuilder start(final OffsetDateTime start) {
    this.start = start;
    return this;
  }

  public FixedFlowNodeDateFilterBuilder end(final OffsetDateTime end) {
    this.end = end;
    return this;
  }

  public FixedFlowNodeDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<FlowNodeDateFilterDataDto<?>> filterDto;
    filterDto = type.equals(FLOW_NODE_START_DATE) ? new FlowNodeStartDateFilterDto() : new FlowNodeEndDateFilterDto();
    filterDto.setData(new FixedFlowNodeDateFilterDataDto(flowNodeIds, start, end));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }

}
