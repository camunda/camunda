/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CanceledFlowNodeFilterBuilder {

  private final List<String> values = new ArrayList<>();
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private CanceledFlowNodeFilterBuilder(final ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static CanceledFlowNodeFilterBuilder construct(
      final ProcessFilterBuilder processFilterBuilder) {
    return new CanceledFlowNodeFilterBuilder(processFilterBuilder);
  }

  public CanceledFlowNodeFilterBuilder id(final String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public CanceledFlowNodeFilterBuilder ids(final String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public CanceledFlowNodeFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final CanceledFlowNodeFilterDataDto dataDto = new CanceledFlowNodeFilterDataDto();
    dataDto.setValues(new ArrayList<>(values));
    final CanceledFlowNodeFilterDto canceledFlowNodeFilterDto = new CanceledFlowNodeFilterDto();
    canceledFlowNodeFilterDto.setData(dataDto);
    canceledFlowNodeFilterDto.setFilterLevel(filterLevel);
    filterBuilder.addFilter(canceledFlowNodeFilterDto);
    return filterBuilder;
  }
}
