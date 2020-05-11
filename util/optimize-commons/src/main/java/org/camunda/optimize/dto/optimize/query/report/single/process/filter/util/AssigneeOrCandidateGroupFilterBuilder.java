/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import com.google.common.base.Function;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.AssigneeCandidateGroupFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.IN;
import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.NOT_IN;

public class AssigneeOrCandidateGroupFilterBuilder {

  private String operator = IN;
  private List<String> values = new ArrayList<>();
  private Function<AssigneeCandidateGroupFilterDataDto, ProcessFilterDto<AssigneeCandidateGroupFilterDataDto>> filterCreator;
  private ProcessFilterBuilder filterBuilder;

  private AssigneeOrCandidateGroupFilterBuilder(
    ProcessFilterBuilder processFilterBuilder,
    Function<AssigneeCandidateGroupFilterDataDto, ProcessFilterDto<AssigneeCandidateGroupFilterDataDto>> filterCreator) {
    this.filterBuilder = processFilterBuilder;
    this.filterCreator = filterCreator;
  }

  public static AssigneeOrCandidateGroupFilterBuilder constructAssigneeFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    return new AssigneeOrCandidateGroupFilterBuilder(processFilterBuilder, AssigneeFilterDto::new);
  }

  public static AssigneeOrCandidateGroupFilterBuilder constructCandidateGroupFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    return new AssigneeOrCandidateGroupFilterBuilder(processFilterBuilder, CandidateGroupFilterDto::new);
  }

  public AssigneeOrCandidateGroupFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public AssigneeOrCandidateGroupFilterBuilder inOperator() {
    operator = IN;
    return this;
  }

  public AssigneeOrCandidateGroupFilterBuilder operator(String operator) {
    this.operator = operator;
    return this;
  }

  public AssigneeOrCandidateGroupFilterBuilder notInOperator() {
    operator = NOT_IN;
    return this;
  }

  public AssigneeOrCandidateGroupFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(filterCreator.apply(new AssigneeCandidateGroupFilterDataDto(operator, values)));
    return filterBuilder;
  }

}
