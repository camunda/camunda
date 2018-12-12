package org.camunda.optimize.dto.optimize.query.report.single.process.result;

import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

public class ProcessReportNumberResultDto extends ProcessReportResultDto {

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
