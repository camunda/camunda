/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;

import java.util.List;
import java.util.Optional;

public class OpenIncidentFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;
  private List<String> appliedTo;

  private OpenIncidentFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static OpenIncidentFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new OpenIncidentFilterBuilder(filterBuilder);
  }

  public OpenIncidentFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public OpenIncidentFilterBuilder appliedTo(final String appliedTo) {
    return appliedTo(List.of(appliedTo));
  }

  public OpenIncidentFilterBuilder appliedTo(final List<String> appliedTo) {
    this.appliedTo = appliedTo;
    return this;
  }

  public ProcessFilterBuilder add() {
    final OpenIncidentFilterDto filter = new OpenIncidentFilterDto();
    filter.setFilterLevel(filterLevel);
    Optional.ofNullable(appliedTo).ifPresent(value -> filter.setAppliedTo(appliedTo));
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
