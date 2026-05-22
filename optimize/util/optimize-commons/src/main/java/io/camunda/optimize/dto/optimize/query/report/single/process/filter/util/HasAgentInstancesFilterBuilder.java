/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.HasAgentInstancesFilterDto;

public final class HasAgentInstancesFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;

  private HasAgentInstancesFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  public static HasAgentInstancesFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new HasAgentInstancesFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    final HasAgentInstancesFilterDto filter = new HasAgentInstancesFilterDto();
    filter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
