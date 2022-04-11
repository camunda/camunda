/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;

public class CanceledFlowNodesOnlyFilterBuilder {

  private ProcessFilterBuilder filterBuilder;
  private FilterApplicationLevel filterLevel = FilterApplicationLevel.VIEW;

  private CanceledFlowNodesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static CanceledFlowNodesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new CanceledFlowNodesOnlyFilterBuilder(filterBuilder);
  }

  public CanceledFlowNodesOnlyFilterBuilder filterLevel(final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
    return this;
  }

  public ProcessFilterBuilder add() {
    final CanceledFlowNodesOnlyFilterDto filter = new CanceledFlowNodesOnlyFilterDto();
    filter.setFilterLevel(filterLevel);
    filterBuilder.addFilter(filter);
    return filterBuilder;
  }

}
