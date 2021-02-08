/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;

public class IdentityLinkFilterBuilder {

  private FilterOperator operator = IN;
  private List<String> values = new ArrayList<>();
  private Function<IdentityLinkFilterDataDto, ProcessFilterDto<IdentityLinkFilterDataDto>> filterCreator;
  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private IdentityLinkFilterBuilder(
    ProcessFilterBuilder processFilterBuilder,
    Function<IdentityLinkFilterDataDto, ProcessFilterDto<IdentityLinkFilterDataDto>> filterCreator) {
    this.filterBuilder = processFilterBuilder;
    this.filterCreator = filterCreator;
  }

  public static IdentityLinkFilterBuilder constructAssigneeFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    return new IdentityLinkFilterBuilder(processFilterBuilder, AssigneeFilterDto::new);
  }

  public static IdentityLinkFilterBuilder constructCandidateGroupFilterBuilder(ProcessFilterBuilder processFilterBuilder) {
    return new IdentityLinkFilterBuilder(processFilterBuilder, CandidateGroupFilterDto::new);
  }

  public IdentityLinkFilterBuilder id(String idToFilterFor) {
    values.add(idToFilterFor);
    return this;
  }

  public IdentityLinkFilterBuilder inOperator() {
    operator = IN;
    return this;
  }

  public IdentityLinkFilterBuilder operator(FilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public IdentityLinkFilterBuilder notInOperator() {
    operator = NOT_IN;
    return this;
  }

  public IdentityLinkFilterBuilder ids(String... idsToFilterFor) {
    values.addAll(Arrays.asList(idsToFilterFor));
    return this;
  }

  public IdentityLinkFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final ProcessFilterDto<IdentityLinkFilterDataDto> filter =
      filterCreator.apply(new IdentityLinkFilterDataDto(operator, values));
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
