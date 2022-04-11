/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;

public class NonSuspendedInstancesOnlyFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private NonSuspendedInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static NonSuspendedInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new NonSuspendedInstancesOnlyFilterBuilder(filterBuilder);
  }

  public NonSuspendedInstancesOnlyFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final NonSuspendedInstancesOnlyFilterDto filter = new NonSuspendedInstancesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }
}
