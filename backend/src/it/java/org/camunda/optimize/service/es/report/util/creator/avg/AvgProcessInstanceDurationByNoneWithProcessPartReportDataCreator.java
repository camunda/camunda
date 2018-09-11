package org.camunda.optimize.service.es.report.util.creator.avg;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationHeatMapGroupByNoneWithProcessPart;

public class AvgProcessInstanceDurationByNoneWithProcessPartReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... eventIds) {
    return createAvgPiDurationHeatMapGroupByNoneWithProcessPart(
        processDefinitionKey, processDefinitionVersion, eventIds[0], eventIds[1]);
  }
}
