/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.RollingFlowNodeDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;

public class RollingFlowNodeDateFilterBuilder {
  private ProcessFilterBuilder filterBuilder;
  private List<String> flowNodeIds;
  private RollingDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.VIEW;

  private RollingFlowNodeDateFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static RollingFlowNodeDateFilterBuilder startDate(ProcessFilterBuilder filterBuilder) {
    RollingFlowNodeDateFilterBuilder builder = new RollingFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_START_DATE;
    return builder;
  }

  public static RollingFlowNodeDateFilterBuilder endDate(ProcessFilterBuilder filterBuilder) {
    RollingFlowNodeDateFilterBuilder builder = new RollingFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_END_DATE;
    return builder;
  }

  public RollingFlowNodeDateFilterBuilder start(Long value, DateUnit unit) {
    this.start = new RollingDateFilterStartDto(value, unit);
    return this;
  }

  public RollingFlowNodeDateFilterBuilder flowNodeIds(final List<String> flowNodeIds) {
    this.flowNodeIds = flowNodeIds;
    return this;
  }

  public RollingFlowNodeDateFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    ProcessFilterDto<FlowNodeDateFilterDataDto<?>> filterDto;
    filterDto = type.equals(FLOW_NODE_START_DATE) ? new FlowNodeStartDateFilterDto() : new FlowNodeEndDateFilterDto();
    filterDto.setData(new RollingFlowNodeDateFilterDataDto(flowNodeIds, start));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }
}
