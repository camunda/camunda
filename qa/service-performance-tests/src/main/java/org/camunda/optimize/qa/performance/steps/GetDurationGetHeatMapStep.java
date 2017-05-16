package org.camunda.optimize.qa.performance.steps;

import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import ru.yandex.qatools.allure.annotations.Step;

public class GetDurationGetHeatMapStep extends GetHeatMapStep {

  public GetDurationGetHeatMapStep(FilterMapDto filter) {
    super(filter);
  }

  @Override
  String getRestEndpoint(PerfTestContext context) {
    return context.getConfiguration().getDurationHeatMapEndpoint();
  }

  @Override
  @Step("Query Duration heatmap data over REST API")
  public PerfTestStepResult<HeatMapResponseDto> execute(PerfTestContext context) {
    return super.execute(context);
  }
}
