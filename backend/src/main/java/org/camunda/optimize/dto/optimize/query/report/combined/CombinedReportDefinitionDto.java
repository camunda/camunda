package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;

public class CombinedReportDefinitionDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionDto() {
    this.reportType = COMBINED_REPORT_TYPE;
  }
}
