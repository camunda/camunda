package org.camunda.optimize.qa.performance.steps;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import ru.yandex.qatools.allure.annotations.Step;

import java.util.List;

import static org.camunda.optimize.test.util.ReportDataHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;

public class GetFrequencyGetHeatMapStep extends GetHeatMapStep {

  public GetFrequencyGetHeatMapStep(List<FilterDto> filter) {
    super(filter);
  }

  @Override
  String getRestEndpoint(PerfTestContext context) {
    return context.getConfiguration().getReportEndpoint();
  }

  @Override
  @Step("Query Frequency heatmap data over REST API")
  public PerfTestStepResult<MapReportResultDto> execute(PerfTestContext context) {
    return super.execute(context);
  }

  @Override
  protected ReportDataDto createRequest(String processDefinitionKey, String processDefinitionVersion) {
    return createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinitionKey, processDefinitionVersion);
  }
}
