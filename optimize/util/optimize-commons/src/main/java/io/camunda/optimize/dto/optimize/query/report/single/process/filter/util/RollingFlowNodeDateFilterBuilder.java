/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_START_DATE;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.RollingFlowNodeDateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.List;

public final class RollingFlowNodeDateFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private List<String> flowNodeIds;
  private RollingDateFilterStartDto start;
  private String type;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.VIEW;

  private RollingFlowNodeDateFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static RollingFlowNodeDateFilterBuilder startDate(
      final ProcessFilterBuilder filterBuilder) {
    final RollingFlowNodeDateFilterBuilder builder =
        new RollingFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_START_DATE;
    return builder;
  }

  public static RollingFlowNodeDateFilterBuilder endDate(final ProcessFilterBuilder filterBuilder) {
    final RollingFlowNodeDateFilterBuilder builder =
        new RollingFlowNodeDateFilterBuilder(filterBuilder);
    builder.type = FLOW_NODE_END_DATE;
    return builder;
  }

  public RollingFlowNodeDateFilterBuilder start(final Long value, final DateUnit unit) {
    start = new RollingDateFilterStartDto(value, unit);
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
    final ProcessFilterDto<FlowNodeDateFilterDataDto<?>> filterDto;
    filterDto =
        type.equals(FLOW_NODE_START_DATE)
            ? new FlowNodeStartDateFilterDto()
            : new FlowNodeEndDateFilterDto();
    filterDto.setData(new RollingFlowNodeDateFilterDataDto(flowNodeIds, start));
    filterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filterDto);
    return filterBuilder;
  }
}
