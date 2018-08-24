package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByStartDateReport;

public class MedianProcessInstanceDurationByStartDateReportDataCreator implements ReportDataCreator {

  @Override
  public SingleReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... additional) {
    if (additional.length == 0) {
      return createMedianProcessInstanceDurationGroupByStartDateReport(
        processDefinitionKey, processDefinitionVersion, DATE_UNIT_DAY);
    } else {
      String dateUnit = additional[0];
      return createMedianProcessInstanceDurationGroupByStartDateReport(
        processDefinitionKey, processDefinitionVersion, dateUnit);
    }
  }
}
