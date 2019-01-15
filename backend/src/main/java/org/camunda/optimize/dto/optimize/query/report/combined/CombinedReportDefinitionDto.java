package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class CombinedReportDefinitionDto extends ReportDefinitionDto {

  protected CombinedReportDataDto data;

  public CombinedReportDefinitionDto(CombinedReportDataDto data) {
    this();
    setData(data);
  }

  public CombinedReportDefinitionDto() {
    super(true, ReportType.PROCESS);
  }

  public CombinedReportDataDto getData() {
    return data;
  }

  public void setData(CombinedReportDataDto data) {
    this.data = data;
  }
}
