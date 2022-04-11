/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;

import java.util.List;
import java.util.Optional;

public class ResolvedIncidentFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private ResolvedIncidentFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static ResolvedIncidentFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new ResolvedIncidentFilterBuilder(filterBuilder);
  }

  public ResolvedIncidentFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ResolvedIncidentFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public ResolvedIncidentFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    final ResolvedIncidentFilterDto filter = new ResolvedIncidentFilterDto();
    filter.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> filter.setAppliedTo(appliedTo));
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
