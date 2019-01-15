package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public abstract class DecisionReportResultDto extends SingleDecisionReportDefinitionDto
  implements ReportResultDto {

  protected long decisionInstanceCount;

  public long getDecisionInstanceCount() {
    return decisionInstanceCount;
  }

  public void setDecisionInstanceCount(final long decisionInstanceCount) {
    this.decisionInstanceCount = decisionInstanceCount;
  }

  public abstract ResultType getResultType();

}
