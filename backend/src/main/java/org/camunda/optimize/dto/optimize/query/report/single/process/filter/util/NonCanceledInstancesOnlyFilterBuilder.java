/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;

public class NonCanceledInstancesOnlyFilterBuilder {
  private ProcessFilterBuilder filterBuilder;

  private NonCanceledInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static NonCanceledInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new NonCanceledInstancesOnlyFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new NonCanceledInstancesOnlyFilterDto());
    return filterBuilder;
  }
}
