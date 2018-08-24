package org.camunda.optimize.dto.optimize.query.report.single;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;

public class SingleReportDefinitionDto extends ReportDefinitionDto<SingleReportDataDto> {

  public SingleReportDefinitionDto() {
    this.reportType = SINGLE_REPORT_TYPE;
  }
}
