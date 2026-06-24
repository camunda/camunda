/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.UserTaskFlowNodesOnlyFilterDto;

public final class InstancesContainingUserTasksFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private InstancesContainingUserTasksFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static InstancesContainingUserTasksFilterBuilder construct(
      final ProcessFilterBuilder filterBuilder) {
    return new InstancesContainingUserTasksFilterBuilder(filterBuilder);
  }

  public InstancesContainingUserTasksFilterBuilder filterLevel(
      final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final UserTaskFlowNodesOnlyFilterDto filter = new UserTaskFlowNodesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
