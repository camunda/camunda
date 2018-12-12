package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class DecisionReportNumberResultDto extends DecisionReportResultDto {

  private long result;

  public long getResult() {
    return result;
  }

  public void setResult(long result) {
    this.result = result;
  }

  @Override
  public ResultType getResultType() {
    return ResultType.NUMBER;
  }
}
