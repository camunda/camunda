package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;

import java.util.Collections;

public class CanceledInstancesOnlyFilterBuilder {
  private ProcessFilterBuilder filterBuilder;

  private CanceledInstancesOnlyFilterBuilder(ProcessFilterBuilder filterBuilder) {
    this.filterBuilder = filterBuilder;
  }

  static CanceledInstancesOnlyFilterBuilder construct(ProcessFilterBuilder filterBuilder) {
    return new CanceledInstancesOnlyFilterBuilder(filterBuilder);
  }

  public ProcessFilterBuilder add() {
    filterBuilder.addFilter(new CanceledInstancesOnlyFilterDto());
    return filterBuilder;
  }
}
