/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.WithResolvedIncidentsOnlyFilterDto;

public class WithResolvedIncidentsOnlyFilterBuilder {
  private ProcessFilterBuilder filterBuilder;

  private WithResolvedIncidentsOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static WithResolvedIncidentsOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new WithResolvedIncidentsOnlyFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new WithResolvedIncidentsOnlyFilterDto());
    return filterBuilder;
  }
}
