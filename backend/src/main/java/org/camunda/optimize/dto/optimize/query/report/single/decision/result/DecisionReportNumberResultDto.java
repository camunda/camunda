package org.camunda.optimize.dto.optimize.query.report.single.decision.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class DecisionReportNumberResultDto extends DecisionReportResultDto {

  private long data;

  public long getData() {
    return data;
  }

  public void setData(long data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    return ResultType.FREQUENCY_NUMBER;
  }
}
