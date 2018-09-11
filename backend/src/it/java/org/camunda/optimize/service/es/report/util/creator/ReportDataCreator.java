package org.camunda.optimize.service.es.report.util.creator;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;

public interface ReportDataCreator {

  SingleReportDataDto create(String processDefinitionVersion, String processDefinitionKey, String... additional);
}
