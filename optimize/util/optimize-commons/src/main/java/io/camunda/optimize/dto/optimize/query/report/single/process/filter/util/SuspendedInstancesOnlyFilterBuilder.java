/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;

public final class SuspendedInstancesOnlyFilterBuilder {

  private final ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private SuspendedInstancesOnlyFilterBuilder(final ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static SuspendedInstancesOnlyFilterBuilder construct(final ProcessFilterBuilder filterBuilder) {
    return new SuspendedInstancesOnlyFilterBuilder(filterBuilder);
  }

  public SuspendedInstancesOnlyFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final SuspendedInstancesOnlyFilterDto filter = new SuspendedInstancesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
