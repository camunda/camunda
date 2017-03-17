package org.camunda.optimize.qa.performance.steps;

import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;

public class GetFrequencyHeatMapStep extends HeatMapStep {

  public GetFrequencyHeatMapStep(FilterMapDto filter) {
    super(filter);
  }

  @Override
  String getRestEndpoint(PerfTestContext context) {
    return context.getConfiguration().getFrequencyHeatMapEndpoint();
  }
}
