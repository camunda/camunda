package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class CombinedReportDefinitionDto extends ReportDefinitionDto<CombinedReportDataDto> {

  public CombinedReportDefinitionDto() {
    this(new CombinedReportDataDto());
  }

  public CombinedReportDefinitionDto(CombinedReportDataDto data) {
    super(data, true, ReportType.PROCESS);
  }

}
