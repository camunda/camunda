package org.camunda.optimize.dto.optimize.query.report.single.decision;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class SingleDecisionReportDefinitionDto extends ReportDefinitionDto<DecisionReportDataDto> {

  public SingleDecisionReportDefinitionDto() {
    super(new DecisionReportDataDto(), false, ReportType.DECISION);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

}
