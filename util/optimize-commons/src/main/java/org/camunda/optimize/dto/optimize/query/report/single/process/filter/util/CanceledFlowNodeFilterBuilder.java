/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CanceledFlowNodeFilterBuilder {

  private List<String> values = new ArrayList<>();
  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private CanceledFlowNodeFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static CanceledFlowNodeFilterBuilder construct(ProcessFilterBuilder processFilterBuilder) {
    return new CanceledFlowNodeFilterBuilder(processFilterBuilder);
  }

  public CanceledFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public CanceledFlowNodeFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public CanceledFlowNodeFilterBuilder filterLevel(FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    CanceledFlowNodeFilterDataDto dataDto = new CanceledFlowNodeFilterDataDto();
    dataDto.setValues(new ArrayList<>(values));
    CanceledFlowNodeFilterDto canceledFlowNodeFilterDto = new CanceledFlowNodeFilterDto();
    canceledFlowNodeFilterDto.setData(dataDto);
    canceledFlowNodeFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(canceledFlowNodeFilterDto);
    return filterBuilder;
  }
}
