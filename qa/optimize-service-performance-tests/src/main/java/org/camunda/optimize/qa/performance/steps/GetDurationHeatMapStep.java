package org.camunda.optimize.qa.performance.steps;

import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;

public class GetDurationHeatMapStep extends HeatMapStep {

  public GetDurationHeatMapStep(FilterMapDto filter) {
    super(filter);
  }

  @Override
  String getRestEndpoint(PerfTestContext context) {
    return context.getConfiguration().getDurationHeatMapEndpoint();
  }
}
