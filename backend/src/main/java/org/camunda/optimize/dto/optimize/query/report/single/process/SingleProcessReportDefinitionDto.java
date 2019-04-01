package org.camunda.optimize.dto.optimize.query.report.single.process;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class SingleProcessReportDefinitionDto extends ReportDefinitionDto<ProcessReportDataDto> {

  public SingleProcessReportDefinitionDto() {
    super(new ProcessReportDataDto(), false, ReportType.PROCESS);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

}
