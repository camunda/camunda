/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;

public class NonSuspendedInstancesOnlyFilterBuilder {
  private ProcessFilterBuilder filterBuilder;

  private NonSuspendedInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static NonSuspendedInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new NonSuspendedInstancesOnlyFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new NonSuspendedInstancesOnlyFilterDto());
    return filterBuilder;
  }
}
