package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationHeatMapGroupByNone;

public class MinProcessInstanceDurationByNoneReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... ignored) {
    return createMinProcessInstanceDurationHeatMapGroupByNone(
        processDefinitionKey, processDefinitionVersion);
  }
}
