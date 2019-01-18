package org.camunda.optimize.dto.optimize.query.report.single.decision;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportType;

public class SingleDecisionReportDefinitionDto extends ReportDefinitionDto {

  protected DecisionReportDataDto data = new DecisionReportDataDto();

  public SingleDecisionReportDefinitionDto() {
    super(false, ReportType.DECISION);
  }

  @Override
  public ReportType getReportType() {
    return super.getReportType();
  }

  public DecisionReportDataDto getData() {
    return data;
  }

  public void setData(DecisionReportDataDto data) {
    this.data = data;
  }
}
