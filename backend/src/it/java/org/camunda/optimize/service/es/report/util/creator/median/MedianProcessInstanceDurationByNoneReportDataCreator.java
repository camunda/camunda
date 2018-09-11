package org.camunda.optimize.service.es.report.util.creator.median;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationHeatMapGroupByNone;

public class MedianProcessInstanceDurationByNoneReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... ignored) {
    return createMedianProcessInstanceDurationHeatMapGroupByNone(
        processDefinitionKey, processDefinitionVersion);
  }
}
