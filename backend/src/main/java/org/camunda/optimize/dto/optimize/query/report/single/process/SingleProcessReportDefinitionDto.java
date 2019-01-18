package org.camunda.optimize.dto.optimize.query.report.single.process;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class SingleProcessReportDefinitionDto extends ReportDefinitionDto {

  protected ProcessReportDataDto data = new ProcessReportDataDto();

  public SingleProcessReportDefinitionDto() {
    super(false, ReportType.PROCESS);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

  public ProcessReportDataDto getData() {
    return data;
  }

  public void setData(ProcessReportDataDto data) {
    this.data = data;
  }
}
