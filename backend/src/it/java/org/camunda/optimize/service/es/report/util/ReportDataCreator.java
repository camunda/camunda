package org.camunda.optimize.service.es.report.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;

public interface ReportDataCreator {

  ReportDataDto create(String processDefinitionVersion, String processDefinitionKey, String... additional);
}
