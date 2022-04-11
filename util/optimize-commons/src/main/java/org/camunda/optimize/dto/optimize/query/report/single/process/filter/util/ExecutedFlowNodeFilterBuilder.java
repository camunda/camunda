/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ExecutedFlowNodeFilterBuilder {

  private MembershipFilterOperator membershipFilterOperator = MembershipFilterOperator.IN;
  private final List<String> values = new ArrayList<>();
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

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
    membershipFilterOperator = MembershipFilterOperator.IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder operator(MembershipFilterOperator membershipFilterOperator) {
    this.membershipFilterOperator = membershipFilterOperator;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder notInOperator() {
    membershipFilterOperator = MembershipFilterOperator.NOT_IN;
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

  public ExecutedFlowNodeFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public ExecutedFlowNodeFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    ExecutedFlowNodeFilterDataDto dataDto = new ExecutedFlowNodeFilterDataDto();
    dataDto.setOperator(membershipFilterOperator);
    dataDto.setValues(new ArrayList<>(values));
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setData(dataDto);
    executedFlowNodeFilterDto.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> executedFlowNodeFilterDto.setAppliedTo(appliedTo));
    filterBuilder.addFilter(executedFlowNodeFilterDto);
    return filterBuilder;
  }
}
