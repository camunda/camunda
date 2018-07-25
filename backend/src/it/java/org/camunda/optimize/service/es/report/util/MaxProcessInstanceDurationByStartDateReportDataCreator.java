package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByStartDateReport;

public class MaxProcessInstanceDurationByStartDateReportDataCreator implements ReportDataCreator {

  @Override
  public ReportDataDto create(String processDefinitionKey, String processDefinitionVersion, String... additional) {
    if (additional.length == 0) {
      return createMaxProcessInstanceDurationGroupByStartDateReport(
        processDefinitionKey, processDefinitionVersion, DATE_UNIT_DAY);
    } else {
      String dateUnit = additional[0];
      return createMaxProcessInstanceDurationGroupByStartDateReport(
        processDefinitionKey, processDefinitionVersion, dateUnit);
    }
  }
}
