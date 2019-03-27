package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;

import java.util.Collections;

public class CompletedInstancesOnlyFilterBuilder {
  private ProcessFilterBuilder filterBuilder;

  private CompletedInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }
  static CompletedInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new CompletedInstancesOnlyFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new CompletedInstancesOnlyFilterDto());
    return filterBuilder;
  }
}
