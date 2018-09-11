package org.camunda.optimize.service.es.report.util.creator.min;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.service.es.report.util.creator.ReportDataCreator;

import static org.camunda.optimize.test.util.ReportDataHelper.createMinPiDurationHeatMapGroupByNoneWithProcessPart;

public class MinProcessInstanceDurationByNoneWithProcessPartReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... eventIds) {
    return createMinPiDurationHeatMapGroupByNoneWithProcessPart(
        processDefinitionKey, processDefinitionVersion, eventIds[0], eventIds[1]);
  }
}
