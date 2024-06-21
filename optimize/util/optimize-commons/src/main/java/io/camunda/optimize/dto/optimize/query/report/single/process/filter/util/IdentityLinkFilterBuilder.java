/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.IN;
import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator.NOT_IN;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.IdentityLinkFilterDataDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class IdentityLinkFilterBuilder {

  private MembershipFilterOperator operator = IN;
  private final List<String> values = new ArrayList<>();
  private final Function<IdentityLinkFilterDataDto, ProcessFilterDto<IdentityLinkFilterDataDto>>
      filterCreator;
  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private IdentityLinkFilterBuilder(
      final ProcessFilterBuilder processFilterBuilder,
      final Function<IdentityLinkFilterDataDto, ProcessFilterDto<IdentityLinkFilterDataDto>>
          filterCreator) {
    filterBuilder = processFilterBuilder;
    this.filterCreator = filterCreator;
  }

  public static IdentityLinkFilterBuilder constructAssigneeFilterBuilder(
      final ProcessFilterBuilder processFilterBuilder) {
    return new IdentityLinkFilterBuilder(processFilterBuilder, AssigneeFilterDto::new);
  }

  public static IdentityLinkFilterBuilder constructCandidateGroupFilterBuilder(
      final ProcessFilterBuilder processFilterBuilder) {
    return new IdentityLinkFilterBuilder(processFilterBuilder, CandidateGroupFilterDto::new);
  }

  public IdentityLinkFilterBuilder id(final String idToFilterFor) {
    values.add(idToFilterFor);
    return this;
  }

  public IdentityLinkFilterBuilder inOperator() {
    operator = IN;
    return this;
  }

  public IdentityLinkFilterBuilder operator(final MembershipFilterOperator operator) {
    this.operator = operator;
    return this;
  }

  public IdentityLinkFilterBuilder notInOperator() {
    operator = NOT_IN;
    return this;
  }

  public IdentityLinkFilterBuilder ids(final String... idsToFilterFor) {
    values.addAll(Arrays.asList(idsToFilterFor));
    return this;
  }

  public IdentityLinkFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public IdentityLinkFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public IdentityLinkFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    final ProcessFilterDto<IdentityLinkFilterDataDto> filter =
        filterCreator.apply(new IdentityLinkFilterDataDto(operator, values));
    filter.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> filter.setAppliedTo(appliedTo));
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
