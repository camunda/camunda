/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;

import java.util.List;
import java.util.Optional;

public class CompletedInstancesOnlyFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private CompletedInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static CompletedInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new CompletedInstancesOnlyFilterBuilder(filterBuilder);
  }

  public CompletedInstancesOnlyFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public CompletedInstancesOnlyFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public CompletedInstancesOnlyFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    final CompletedInstancesOnlyFilterDto filter = new CompletedInstancesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> filter.setAppliedTo(appliedTo));
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
