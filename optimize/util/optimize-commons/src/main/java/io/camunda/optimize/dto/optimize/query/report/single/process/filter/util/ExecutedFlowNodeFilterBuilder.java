/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ExecutedFlowNodeFilterBuilder {

  private MembershipFilterOperator membershipFilterOperator = MembershipFilterOperator.IN;
  private final List<String> values = new ArrayList<>();
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private ExecutedFlowNodeFilterBuilder(final ProcessFilterBuilder processFilterBuilder) {
    filterBuilder = processFilterBuilder;
  }

  public static ExecutedFlowNodeFilterBuilder construct(
      final ProcessFilterBuilder processFilterBuilder) {
    return new ExecutedFlowNodeFilterBuilder(processFilterBuilder);
  }

  public ExecutedFlowNodeFilterBuilder id(final String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder inOperator() {
    membershipFilterOperator = MembershipFilterOperator.IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder operator(
      final MembershipFilterOperator membershipFilterOperator) {
    this.membershipFilterOperator = membershipFilterOperator;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder notInOperator() {
    membershipFilterOperator = MembershipFilterOperator.NOT_IN;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder ids(final String... flowNodeIds) {
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
    final ExecutedFlowNodeFilterDataDto dataDto = new ExecutedFlowNodeFilterDataDto();
    dataDto.setOperator(membershipFilterOperator);
    dataDto.setValues(new ArrayList<>(values));
    final ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setData(dataDto);
    executedFlowNodeFilterDto.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo)
        .ifPresent(value -> executedFlowNodeFilterDto.setAppliedTo(appliedTo));
    filterBuilder.addFilter(executedFlowNodeFilterDto);
    return filterBuilder;
  }
}
