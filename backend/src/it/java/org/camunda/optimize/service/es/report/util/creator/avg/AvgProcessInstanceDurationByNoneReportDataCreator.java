package org.camunda.optimize.service.es.report.util.creator.avg;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationHeatMapGroupByNone;

public class AvgProcessInstanceDurationByNoneReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... ignored) {
    return createAvgPiDurationHeatMapGroupByNone(
        processDefinitionKey, processDefinitionVersion);
  }
}
