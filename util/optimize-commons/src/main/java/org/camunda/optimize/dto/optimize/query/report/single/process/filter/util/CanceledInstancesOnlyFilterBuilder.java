/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;

public class CanceledInstancesOnlyFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.INSTANCE;

  private CanceledInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static CanceledInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new CanceledInstancesOnlyFilterBuilder(filterBuilder);
  }

  public CanceledInstancesOnlyFilterBuilder filterLevel(FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final CanceledInstancesOnlyFilterDto filter = new CanceledInstancesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
